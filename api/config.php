<?php
declare(strict_types=1);

// Prevent any accidental output
ob_start();

// Error handling - never expose errors to client
error_reporting(E_ALL);
ini_set('display_errors', '0');
ini_set('log_errors', '1');
ini_set('error_log', __DIR__ . '/php_errors.log');

// Configuration
define('BASE_PATH', __DIR__);
define('DATA_DIR', BASE_PATH . '/data');
define('UPLOAD_DIR', BASE_PATH . '/uploads');
define('AVATAR_DIR', UPLOAD_DIR . '/avatars');
define('VIDEO_DIR', UPLOAD_DIR . '/videos');
define('THUMB_DIR', UPLOAD_DIR . '/thumbnails');
define('MAX_VIDEO_SIZE', 100 * 1024 * 1024); // 100MB
define('MAX_AVATAR_SIZE', 5 * 1024 * 1024);  // 5MB
define('SESSION_EXPIRY', 30 * 24 * 60 * 60); // 30 days
define('RATE_LIMIT_WINDOW', 60); // 60 seconds
define('RATE_LIMIT_MAX', 120);   // 120 requests per minute per IP for general
define('RATE_LIMIT_AUTH_MAX', 10); // 10 auth attempts per minute

// Security headers (sent before anything that could fail so that even
// error responses are JSON with CORS headers)
if (!headers_sent()) {
    header('Content-Type: application/json; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header('X-XSS-Protection: 1; mode=block');
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
    header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With');
}

// Handle preflight
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// Last-resort safety net: if anything fatals (uncaught exception, missing
// extension, etc.) still answer with JSON instead of Apache's blank HTML 500.
register_shutdown_function(function (): void {
    $err = error_get_last();
    if ($err === null) return;
    if (!in_array($err['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR, E_USER_ERROR, E_RECOVERABLE_ERROR], true)) return;
    // Discard any partial output
    while (ob_get_level() > 0) {
        @ob_end_clean();
    }
    if (!headers_sent()) {
        http_response_code(500);
        header('Content-Type: application/json; charset=utf-8');
        header('Access-Control-Allow-Origin: *');
    }
    error_log('API fatal: ' . $err['message'] . ' in ' . $err['file'] . ':' . $err['line']);
    echo json_encode(['error' => true, 'message' => 'Internal server error'], JSON_UNESCAPED_UNICODE);
});

// mbstring may be missing on some hosts. Provide byte-based fallbacks so
// register/search do not fatal with "Call to undefined function".
if (!function_exists('mb_substr')) {
    function mb_substr(string $s, int $start, ?int $length = null, ?string $encoding = null): string
    {
        return $length === null ? substr($s, $start) : substr($s, $start, $length);
    }
}
if (!function_exists('mb_strlen')) {
    function mb_strlen(string $s, ?string $encoding = null): int
    {
        return strlen($s);
    }
}
if (!function_exists('mb_strtolower')) {
    function mb_strtolower(string $s, ?string $encoding = null): string
    {
        return strtolower($s);
    }
}

// Ensure directories exist and are writable. Report a JSON error instead of
// letting a later file_put_contents() fatal into a blank 500.
function ensure_writable_dir(string $dir, string $label): void
{
    if (!is_dir($dir)) {
        @mkdir($dir, 0775, true);
    }
    if (!is_dir($dir) || !is_writable($dir)) {
        $rel = str_replace(BASE_PATH, 'api', $dir);
        error_response(
            "Server storage is not writable ({$label}). Run: chmod 775 {$rel} on the server (and make sure it is owned by or group-writable for the PHP user).",
            500
        );
    }
}
ensure_writable_dir(DATA_DIR, 'data directory');
// Upload dirs are only needed for media; create them but do not block login if they fail.
if (!is_dir(AVATAR_DIR)) @mkdir(AVATAR_DIR, 0775, true);
if (!is_dir(VIDEO_DIR)) @mkdir(VIDEO_DIR, 0775, true);
if (!is_dir(THUMB_DIR)) @mkdir(THUMB_DIR, 0775, true);

/**
 * Public scheme+host for building absolute media URLs. Honours reverse
 * proxies / CDNs (Cloudflare etc.) that terminate TLS and talk plain HTTP to
 * Apache; without this uploads got "http://" URLs.
 */
function public_base_url(): string
{
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
        || (($_SERVER['SERVER_PORT'] ?? '') === '443')
        || (strtolower((string)($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')) === 'https')
        || (strtolower((string)($_SERVER['HTTP_X_FORWARDED_SSL'] ?? '')) === 'on')
        || (strtolower((string)($_SERVER['HTTP_FRONT_END_HTTPS'] ?? '')) === 'on');
    $host = $_SERVER['HTTP_X_FORWARDED_HOST'] ?? ($_SERVER['HTTP_HOST'] ?? ($_SERVER['SERVER_NAME'] ?? 'localhost'));
    $host = explode(',', (string)$host)[0];
    $base = rtrim(str_replace('\\', '/', dirname((string)($_SERVER['SCRIPT_NAME'] ?? '/api/index.php'))), '/');
    return ($https ? 'https' : 'http') . '://' . trim($host) . $base;
}

/** Turn a stored (possibly relative or http://) media URL into a usable absolute URL. */
function media_url(?string $url): ?string
{
    if ($url === null || $url === '') return null;
    if (preg_match('#^https?://#i', $url)) return $url;
    return public_base_url() . '/' . ltrim($url, '/');
}

/**
 * JSON database handler with file locking
 */
class JsonDB
{
    private string $file;
    private $handle = null;

    public function __construct(string $name)
    {
        $this->file = DATA_DIR . '/' . $name . '.json';
        if (!file_exists($this->file)) {
            $this->initFile();
        }
    }

    private function initFile(): void
    {
        if (@file_put_contents($this->file, "[]", LOCK_EX) === false) {
            error_response('Server storage is not writable (api/data). Run: chmod 775 api/data on the server.', 500);
        }
        @chmod($this->file, 0664);
    }

    public function read(): array
    {
        $contents = file_get_contents($this->file);
        if ($contents === false || $contents === '') {
            return [];
        }
        $data = json_decode($contents, true);
        return is_array($data) ? $data : [];
    }

    public function write(array $data): bool
    {
        $tmp = $this->file . '.tmp';
        $json = json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
        if ($json === false) return false;

        // Atomic write with lock
        $handle = fopen($tmp, 'c');
        if (!$handle) return false;

        if (!flock($handle, LOCK_EX)) {
            fclose($handle);
            return false;
        }

        ftruncate($handle, 0);
        rewind($handle);
        fwrite($handle, $json);
        fflush($handle);
        flock($handle, LOCK_UN);
        fclose($handle);

        if (!@rename($tmp, $this->file)) {
            @unlink($tmp);
            return false;
        }
        return true;
    }

    /**
     * Append an item with unique ID generation
     */
    public function insert(array $item): ?string
    {
        $data = $this->read();
        $id = $item['id'] ?? $this->generateId();
        $item['id'] = $id;
        $item['created_at'] = $item['created_at'] ?? date('c');
        $data[] = $item;
        if ($this->write($data)) {
            return $id;
        }
        return null;
    }

    /**
     * Update items matching a filter
     */
    public function update(callable $filter, callable $updater): int
    {
        $data = $this->read();
        $count = 0;
        foreach ($data as $key => $item) {
            if ($filter($item)) {
                $data[$key] = $updater($item);
                $count++;
            }
        }
        if ($count > 0) {
            $this->write($data);
        }
        return $count;
    }

    /**
     * Delete items matching a filter
     */
    public function delete(callable $filter): int
    {
        $data = $this->read();
        $originalCount = count($data);
        $data = array_values(array_filter($data, fn($item) => !$filter($item)));
        $deleted = $originalCount - count($data);
        if ($deleted > 0) {
            $this->write($data);
        }
        return $deleted;
    }

    /**
     * Find one item by filter
     */
    public function findOne(callable $filter): ?array
    {
        $data = $this->read();
        foreach ($data as $item) {
            if ($filter($item)) {
                return $item;
            }
        }
        return null;
    }

    /**
     * Find all items matching filter, with pagination and sort
     */
    public function findAll(?callable $filter = null, string $sortBy = 'created_at', bool $desc = true, int $offset = 0, int $limit = 50): array
    {
        $data = $this->read();
        if ($filter) {
            $data = array_values(array_filter($data, $filter));
        }
        // Sort
        usort($data, function ($a, $b) use ($sortBy, $desc) {
            $va = $a[$sortBy] ?? '';
            $vb = $b[$sortBy] ?? '';
            if ($va == $vb) return 0;
            $cmp = is_numeric($va) && is_numeric($vb) ? $va <=> $vb : strcmp((string)$va, (string)$vb);
            return $desc ? -$cmp : $cmp;
        });
        $total = count($data);
        $items = array_slice($data, $offset, $limit);
        return ['items' => $items, 'total' => $total];
    }

    public function count(?callable $filter = null): int
    {
        $data = $this->read();
        if ($filter) {
            $data = array_filter($data, $filter);
        }
        return count($data);
    }

    private function generateId(): string
    {
        return bin2hex(random_bytes(16));
    }
}

// Rate limiting
class RateLimiter
{
    private static string $file = '';

    public static function check(string $key, int $max, int $window): bool
    {
        self::$file = DATA_DIR . '/rate_limits.json';
        $now = time();
        $limits = [];
        if (file_exists(self::$file)) {
            $limits = json_decode(file_get_contents(self::$file), true) ?: [];
        }

        // Clean old entries
        foreach ($limits as $k => $entries) {
            $limits[$k] = array_filter($entries, fn($t) => $now - $t < $window);
        }

        $entries = $limits[$key] ?? [];
        if (count($entries) >= $max) {
            return false;
        }
        $entries[] = $now;
        $limits[$key] = $entries;

        file_put_contents(self::$file, json_encode($limits), LOCK_EX);
        return true;
    }
}

// Response helper
function json_response($data, int $code = 200): void
{
    // Drop any stray output (warnings, BOMs, whitespace) captured so far so
    // the body is pure JSON. Only touch buffers that actually exist:
    // ob_end_flush() with no active buffer throws on PHP 8 and, with
    // zlib.output_compression, would produce a blank HTML 500.
    while (ob_get_level() > 0) {
        if (!@ob_end_clean()) break;
    }
    if (!headers_sent()) {
        http_response_code($code);
        header('Content-Type: application/json; charset=utf-8');
    }
    $json = json_encode($data, JSON_UNESCAPED_UNICODE);
    if ($json === false) {
        $json = json_encode(['error' => true, 'message' => 'Failed to encode response']);
    }
    echo $json;
    exit;
}

function error_response(string $message, int $code = 400, $errors = null): void
{
    $resp = ['error' => true, 'message' => $message];
    if ($errors) $resp['errors'] = $errors;
    json_response($resp, $code);
}

function get_json_body(): array
{
    $body = file_get_contents('php://input');
    if (empty($body)) return $_POST;
    $data = json_decode($body, true);
    return is_array($data) ? $data : [];
}

/**
 * Read the Authorization header in a way that works on Apache mod_php,
 * PHP-CGI and PHP-FPM. getallheaders() does not exist on every SAPI, and
 * Apache strips Authorization unless .htaccess copies it into
 * HTTP_AUTHORIZATION / REDIRECT_HTTP_AUTHORIZATION.
 */
function get_authorization_header(): string
{
    $candidates = [
        $_SERVER['HTTP_AUTHORIZATION'] ?? null,
        $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? null,
        $_SERVER['REDIRECT_REDIRECT_HTTP_AUTHORIZATION'] ?? null,
    ];
    foreach ($candidates as $c) {
        if (is_string($c) && trim($c) !== '') return trim($c);
    }
    if (function_exists('getallheaders')) {
        $headers = @getallheaders();
        if (is_array($headers)) {
            foreach ($headers as $k => $v) {
                if (strcasecmp((string)$k, 'Authorization') === 0 && is_string($v) && trim($v) !== '') {
                    return trim($v);
                }
            }
        }
    }
    return '';
}

function get_bearer_token(): string
{
    $auth = get_authorization_header();
    if ($auth === '') return '';
    if (stripos($auth, 'Bearer') === 0) {
        $auth = substr($auth, 6);
    }
    return trim($auth);
}

function get_user_id(): ?string
{
    $auth = get_authorization_header();
    if (empty($auth)) {
        // Check query param for simpler video streaming clients
        if (isset($_GET['token'])) {
            $token = $_GET['token'];
        } else {
            return null;
        }
    } else {
        $token = get_bearer_token();
    }
    if (empty($token)) return null;

    $sessions = new JsonDB('sessions');
    $session = $sessions->findOne(fn($s) => ($s['token'] ?? '') === $token);
    if (!$session) return null;

    // Check expiry
    if (isset($session['expires_at']) && strtotime($session['expires_at']) < time()) {
        return null;
    }
    return $session['user_id'] ?? null;
}

function require_auth(): string
{
    $userId = get_user_id();
    if (!$userId) {
        error_response('Authentication required', 401);
    }
    return $userId;
}

function sanitize_string(string $s, int $max = 500): string
{
    $s = trim($s);
    $s = strip_tags($s);
    $s = htmlspecialchars($s, ENT_QUOTES | ENT_HTML5, 'UTF-8');
    if (mb_strlen($s) > $max) {
        $s = mb_substr($s, 0, $max);
    }
    return $s;
}

function is_valid_username(string $s): bool
{
    return (bool)preg_match('/^[a-zA-Z0-9_]{3,20}$/', $s);
}

function is_valid_password(string $s): bool
{
    return strlen($s) >= 6 && strlen($s) <= 128;
}

function generate_token(): string
{
    return bin2hex(random_bytes(32));
}

function get_client_ip(): string
{
    return $_SERVER['HTTP_X_FORWARDED_FOR'] ?? $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
}

function format_user(array $user, bool $includeEmail = false): array
{
    $follows = new JsonDB('follows');
    $followersCount = $follows->count(fn($f) => $f['following_id'] === $user['id']);
    $followingCount = $follows->count(fn($f) => $f['follower_id'] === $user['id']);

    $out = [
        'id' => $user['id'],
        'username' => $user['username'],
        'display_name' => $user['display_name'] ?? $user['username'],
        'avatar_url' => media_url($user['avatar_url'] ?? null),
        'bio' => $user['bio'] ?? '',
        'followers_count' => $followersCount,
        'following_count' => $followingCount,
        'created_at' => $user['created_at'] ?? null,
    ];
    if ($includeEmail) {
        $out['email'] = $user['email'] ?? '';
    }
    return $out;
}

function format_video(array $video, ?string $currentUserId = null): array
{
    $likes = new JsonDB('likes');
    $comments = new JsonDB('comments');
    $saves = new JsonDB('saves');

    $likeCount = $likes->count(fn($l) => $l['video_id'] === $video['id']);
    $commentCount = $comments->count(fn($c) => $c['video_id'] === $video['id'] && !isset($c['parent_id']));
    $saveCount = $saves->count(fn($s) => $s['video_id'] === $video['id']);

    $users = new JsonDB('users');
    $author = $users->findOne(fn($u) => $u['id'] === $video['user_id']);
    $authorData = $author ? format_user($author) : null;

    $out = [
        'id' => $video['id'],
        'user_id' => $video['user_id'],
        'author' => $authorData,
        'video_url' => media_url($video['video_url'] ?? ''),
        'thumbnail_url' => media_url($video['thumbnail_url'] ?? null),
        'caption' => $video['caption'] ?? '',
        'hashtags' => $video['hashtags'] ?? [],
        'duration' => (float)($video['duration'] ?? 0),
        'views' => (int)($video['views'] ?? 0),
        'likes_count' => $likeCount,
        'comments_count' => $commentCount,
        'saves_count' => $saveCount,
        'is_liked' => false,
        'is_saved' => false,
        'is_following_author' => false,
        'created_at' => $video['created_at'] ?? null,
    ];

    if ($currentUserId) {
        $out['is_liked'] = (bool)$likes->findOne(fn($l) => $l['video_id'] === $video['id'] && $l['user_id'] === $currentUserId);
        $out['is_saved'] = (bool)$saves->findOne(fn($s) => $s['video_id'] === $video['id'] && $s['user_id'] === $currentUserId);
        if ($author) {
            $follows = new JsonDB('follows');
            $out['is_following_author'] = (bool)$follows->findOne(
                fn($f) => $f['follower_id'] === $currentUserId && $f['following_id'] === $author['id']
            );
        }
    }
    return $out;
}

function extract_hashtags(string $text): array
{
    preg_match_all('/#(\w+)/u', $text, $matches);
    return array_unique(array_map('strtolower', $matches[1] ?? []));
}

// Block check
function is_blocked(string $blockerId, string $blockedId): bool
{
    // We'll store blocks in users.json under a blocks list or in a separate file - simpler: use sessions? Let's add blocks.json
    $blocks = new JsonDB('blocks');
    return (bool)$blocks->findOne(fn($b) => $b['blocker_id'] === $blockerId && $b['blocked_id'] === $blockedId);
}

function get_blocked_ids(string $userId): array
{
    $blocks = new JsonDB('blocks');
    $res = $blocks->findAll(fn($b) => $b['blocker_id'] === $userId, 'created_at', true, 0, 1000);
    return array_map(fn($b) => $b['blocked_id'], $res['items']);
}
