<?php
declare(strict_types=1);
require_once __DIR__ . '/config.php';

// Parse request and route. Everything, including path parsing, is inside the
// try/catch so a missing/odd REQUEST_URI or SCRIPT_NAME never fatals.
try {
    $method = strtoupper((string)($_SERVER['REQUEST_METHOD'] ?? 'GET'));
    $path = resolve_request_path();
    route($method, $path);
} catch (Throwable $e) {
    error_log('API Error: ' . $e->getMessage() . ' in ' . $e->getFile() . ':' . $e->getLine());
    error_response('Internal server error', 500);
}

function resolve_request_path(): string
{
    $requestUri = (string)($_SERVER['REQUEST_URI'] ?? ($_SERVER['PATH_INFO'] ?? '/'));
    $parsed = parse_url($requestUri, PHP_URL_PATH);
    $requestPath = is_string($parsed) && $parsed !== '' ? $parsed : '/';

    // Strip base path (e.g. "/api") derived from SCRIPT_NAME
    $scriptName = (string)($_SERVER['SCRIPT_NAME'] ?? '');
    $base = $scriptName !== '' ? rtrim(str_replace('\\', '/', dirname($scriptName)), '/') : '';
    if ($base !== '' && $base !== '.' && strpos($requestPath, $base) === 0) {
        $requestPath = substr($requestPath, strlen($base));
    }
    // Allow calling /api/index.php/auth/login directly
    if (preg_match('#^/?index\.php(/|$)#', $requestPath)) {
        $requestPath = (string)preg_replace('#^/?index\.php#', '', $requestPath, 1);
    }
    $requestPath = '/' . ltrim($requestPath, '/');
    return rtrim($requestPath, '/') ?: '/';
}

function route(string $method, string $path): void
{
    // Auth endpoints
    if ($path === '/auth/register' && $method === 'POST') { handle_register(); return; }
    if ($path === '/auth/login' && $method === 'POST') { handle_login(); return; }
    if ($path === '/auth/logout' && $method === 'POST') { handle_logout(); return; }
    if ($path === '/auth/me' && $method === 'GET') { handle_me(); return; }

    // Users
    if (preg_match('#^/users/([a-zA-Z0-9]+)$#', $path, $m) && $method === 'GET') { handle_get_user($m[1]); return; }
    if (preg_match('#^/users/([a-zA-Z0-9]+)/videos$#', $path, $m) && $method === 'GET') { handle_user_videos($m[1]); return; }
    if ($path === '/users/profile/edit' && $method === 'POST') { handle_edit_profile(); return; }
    if ($path === '/users/search' && $method === 'GET') { handle_search_users(); return; }

    // Follow
    if (preg_match('#^/users/([a-zA-Z0-9]+)/follow$#', $path, $m) && $method === 'POST') { handle_follow($m[1]); return; }
    if (preg_match('#^/users/([a-zA-Z0-9]+)/follow$#', $path, $m) && $method === 'DELETE') { handle_unfollow($m[1]); return; }
    if (preg_match('#^/users/([a-zA-Z0-9]+)/followers$#', $path, $m) && $method === 'GET') { handle_followers($m[1]); return; }
    if (preg_match('#^/users/([a-zA-Z0-9]+)/following$#', $path, $m) && $method === 'GET') { handle_following($m[1]); return; }

    // Videos
    if ($path === '/videos/upload' && $method === 'POST') { handle_upload_video(); return; }
    if (preg_match('#^/videos/([a-zA-Z0-9]+)$#', $path, $m) && $method === 'GET') { handle_get_video($m[1]); return; }
    if (preg_match('#^/videos/([a-zA-Z0-9]+)$#', $path, $m) && $method === 'DELETE') { handle_delete_video($m[1]); return; }
    if (preg_match('#^/videos/([a-zA-Z0-9]+)/view$#', $path, $m) && $method === 'POST') { handle_view_video($m[1]); return; }

    // Feed
    if ($path === '/feed/for-you' && $method === 'GET') { handle_feed_for_you(); return; }
    if ($path === '/feed/following' && $method === 'GET') { handle_feed_following(); return; }

    // Likes
    if (preg_match('#^/videos/([a-zA-Z0-9]+)/like$#', $path, $m) && $method === 'POST') { handle_like($m[1]); return; }
    if (preg_match('#^/videos/([a-zA-Z0-9]+)/like$#', $path, $m) && $method === 'DELETE') { handle_unlike($m[1]); return; }

    // Saves
    if (preg_match('#^/videos/([a-zA-Z0-9]+)/save$#', $path, $m) && $method === 'POST') { handle_save($m[1]); return; }
    if (preg_match('#^/videos/([a-zA-Z0-9]+)/save$#', $path, $m) && $method === 'DELETE') { handle_unsave($m[1]); return; }

    // Comments
    if (preg_match('#^/videos/([a-zA-Z0-9]+)/comments$#', $path, $m) && $method === 'GET') { handle_get_comments($m[1]); return; }
    if (preg_match('#^/videos/([a-zA-Z0-9]+)/comments$#', $path, $m) && $method === 'POST') { handle_add_comment($m[1]); return; }
    if (preg_match('#^/comments/([a-zA-Z0-9]+)/like$#', $path, $m) && $method === 'POST') { handle_like_comment($m[1]); return; }
    if (preg_match('#^/comments/([a-zA-Z0-9]+)/like$#', $path, $m) && $method === 'DELETE') { handle_unlike_comment($m[1]); return; }
    if (preg_match('#^/comments/([a-zA-Z0-9]+)/replies$#', $path, $m) && $method === 'GET') { handle_comment_replies($m[1]); return; }
    if (preg_match('#^/comments/([a-zA-Z0-9]+)$#', $path, $m) && $method === 'DELETE') { handle_delete_comment($m[1]); return; }

    // Saved videos
    if ($path === '/users/me/saved' && $method === 'GET') { handle_my_saved(); return; }

    // Notifications
    if ($path === '/notifications' && $method === 'GET') { handle_notifications(); return; }
    if ($path === '/notifications/read' && $method === 'POST') { handle_mark_notifications_read(); return; }

    // Search
    if ($path === '/search' && $method === 'GET') { handle_search(); return; }

    // Hashtags
    if ($path === '/hashtags/trending' && $method === 'GET') { handle_trending_hashtags(); return; }
    if (preg_match('#^/hashtags/([^/]+)/videos$#', $path, $m) && $method === 'GET') { handle_hashtag_videos($m[1]); return; }

    // Reports & Blocks
    if ($path === '/report' && $method === 'POST') { handle_report(); return; }
    if (preg_match('#^/users/([a-zA-Z0-9]+)/block$#', $path, $m) && $method === 'POST') { handle_block($m[1]); return; }
    if (preg_match('#^/users/([a-zA-Z0-9]+)/block$#', $path, $m) && $method === 'DELETE') { handle_unblock($m[1]); return; }

    // Upload avatar
    if ($path === '/users/avatar' && $method === 'POST') { handle_upload_avatar(); return; }

    // Stream video (direct file access but through PHP for byte-range support)
    if (preg_match('#^/stream/([a-zA-Z0-9_.]+)$#', $path, $m) && $method === 'GET') { handle_stream($m[1], 'videos'); return; }
    if (preg_match('#^/avatar/([a-zA-Z0-9_.]+)$#', $path, $m) && $method === 'GET') { handle_stream($m[1], 'avatars'); return; }

    error_response('Not found', 404);
}

// ================== AUTH ==================
function handle_register(): void
{
    $ip = get_client_ip();
    if (!RateLimiter::check("reg:$ip", 5, RATE_LIMIT_WINDOW)) {
        error_response('Too many requests. Try again later.', 429);
    }

    $body = get_json_body();
    $username = trim($body['username'] ?? '');
    $email = trim($body['email'] ?? '');
    $password = $body['password'] ?? '';
    $displayName = trim($body['display_name'] ?? $username);

    if (!is_valid_username($username)) error_response('Username must be 3-20 chars, letters/numbers/underscore only', 400);
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) error_response('Invalid email', 400);
    if (!is_valid_password($password)) error_response('Password must be 6-128 chars', 400);
    if (empty($displayName)) $displayName = $username;

    $users = new JsonDB('users');
    if ($users->findOne(fn($u) => $u['username'] === $username)) error_response('Username already taken', 409);
    if ($users->findOne(fn($u) => strtolower($u['email'] ?? '') === strtolower($email))) error_response('Email already registered', 409);

    $id = bin2hex(random_bytes(16));
    $user = [
        'id' => $id,
        'username' => $username,
        'email' => strtolower($email),
        'password_hash' => password_hash($password, PASSWORD_BCRYPT, ['cost' => 10]),
        'display_name' => mb_substr($displayName, 0, 50),
        'bio' => '',
        'avatar_url' => null,
        'created_at' => date('c'),
    ];
    $users->insert($user);

    // Create session
    $token = create_session($id);

    json_response([
        'token' => $token,
        'user' => format_user($user),
    ], 201);
}

function handle_login(): void
{
    $ip = get_client_ip();
    if (!RateLimiter::check("login:$ip", RATE_LIMIT_AUTH_MAX, RATE_LIMIT_WINDOW)) {
        error_response('Too many login attempts. Try again later.', 429);
    }

    $body = get_json_body();
    $login = trim($body['login'] ?? '');  // username or email
    $password = $body['password'] ?? '';

    if (empty($login) || empty($password)) error_response('Login and password required', 400);

    $users = new JsonDB('users');
    $user = $users->findOne(function ($u) use ($login) {
        return $u['username'] === $login || strtolower($u['email'] ?? '') === strtolower($login);
    });
    if (!$user || !password_verify($password, $user['password_hash'] ?? '')) {
        error_response('Invalid credentials', 401);
    }

    $token = create_session($user['id']);
    json_response([
        'token' => $token,
        'user' => format_user($user),
    ]);
}

function handle_logout(): void
{
    $userId = get_user_id();
    if ($userId) {
        $token = get_bearer_token();
        $sessions = new JsonDB('sessions');
        $sessions->delete(fn($s) => $s['token'] === $token);
    }
    json_response(['success' => true]);
}

function handle_me(): void
{
    $userId = require_auth();
    $users = new JsonDB('users');
    $user = $users->findOne(fn($u) => $u['id'] === $userId);
    if (!$user) error_response('User not found', 404);
    json_response(['user' => format_user($user, true)]);
}

function create_session(string $userId): string
{
    $sessions = new JsonDB('sessions');
    $token = generate_token();
    $sessions->insert([
        'user_id' => $userId,
        'token' => $token,
        'expires_at' => date('c', time() + SESSION_EXPIRY),
        'ip' => get_client_ip(),
    ]);
    return $token;
}

// ================== USERS ==================
function handle_get_user(string $idOrUsername): void
{
    $currentUserId = get_user_id();
    $users = new JsonDB('users');
    // Try by username first, then id
    $user = $users->findOne(fn($u) => $u['username'] === $idOrUsername);
    if (!$user) $user = $users->findOne(fn($u) => $u['id'] === $idOrUsername);
    if (!$user) error_response('User not found', 404);

    $data = format_user($user);
    if ($currentUserId) {
        $follows = new JsonDB('follows');
        $data['is_following'] = (bool)$follows->findOne(
            fn($f) => $f['follower_id'] === $currentUserId && $f['following_id'] === $user['id']
        );
        $data['is_blocked'] = is_blocked($currentUserId, $user['id']);
    } else {
        $data['is_following'] = false;
        $data['is_blocked'] = false;
    }
    json_response(['user' => $data]);
}

function handle_user_videos(string $idOrUsername): void
{
    $currentUserId = get_user_id();
    $users = new JsonDB('users');
    $user = $users->findOne(fn($u) => $u['username'] === $idOrUsername);
    if (!$user) $user = $users->findOne(fn($u) => $u['id'] === $idOrUsername);
    if (!$user) error_response('User not found', 404);

    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(30, max(1, (int)($_GET['limit'] ?? 20)));
    $offset = ($page - 1) * $limit;

    $videos = new JsonDB('videos');
    $blockedIds = $currentUserId ? get_blocked_ids($currentUserId) : [];
    $res = $videos->findAll(
        fn($v) => $v['user_id'] === $user['id'] && !in_array($v['user_id'], $blockedIds),
        'created_at', true, $offset, $limit
    );

    $items = array_map(fn($v) => format_video($v, $currentUserId), $res['items']);
    json_response(['videos' => $items, 'page' => $page, 'has_more' => count($items) === $limit, 'total' => $res['total']]);
}

function handle_edit_profile(): void
{
    $userId = require_auth();
    $body = get_json_body();
    $users = new JsonDB('users');
    $user = $users->findOne(fn($u) => $u['id'] === $userId);
    if (!$user) error_response('Not found', 404);

    $updated = $user;
    if (isset($body['display_name'])) {
        $dn = sanitize_string($body['display_name'], 50);
        if (empty($dn)) error_response('Display name cannot be empty', 400);
        $updated['display_name'] = $dn;
    }
    if (isset($body['bio'])) {
        $updated['bio'] = sanitize_string($body['bio'], 200);
    }
    if (isset($body['username'])) {
        $un = trim($body['username']);
        if (!is_valid_username($un)) error_response('Invalid username', 400);
        if ($un !== $user['username']) {
            if ($users->findOne(fn($u) => $u['username'] === $un)) error_response('Username taken', 409);
            $updated['username'] = $un;
        }
    }
    $users->update(fn($u) => $u['id'] === $userId, fn($u) => $updated);
    json_response(['user' => format_user($updated, true)]);
}

function handle_upload_avatar(): void
{
    $userId = require_auth();
    if (!isset($_FILES['file']) || $_FILES['file']['error'] !== UPLOAD_ERR_OK) {
        error_response('File upload required', 400);
    }
    $file = $_FILES['file'];
    if ($file['size'] > MAX_AVATAR_SIZE) error_response('Avatar too large (max 5MB)', 400);
    $ext = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
    if (!in_array($ext, ['jpg', 'jpeg', 'png', 'webp'])) error_response('Invalid image type', 400);

    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime = $finfo->file($file['tmp_name']);
    if (!in_array($mime, ['image/jpeg', 'image/png', 'image/webp'])) error_response('Invalid image', 400);

    $filename = $userId . '_' . bin2hex(random_bytes(4)) . '.' . $ext;
    $dest = AVATAR_DIR . '/' . $filename;
    // Path traversal protection
    if (strpos(realpath(dirname($dest)), AVATAR_DIR) !== 0) error_response('Invalid path', 400);

    if (!move_uploaded_file($file['tmp_name'], $dest)) error_response('Upload failed', 500);
    chmod($dest, 0644);

    // Determine if the API is at a sub-path (e.g. /api/index.php)
    $basePath = dirname($_SERVER['SCRIPT_NAME']);
    $basePath = rtrim($basePath, '/');
    $avatarPath = $basePath . '/avatar/' . $filename;
    $fullUrl = (isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off' ? 'https' : 'http') . '://' . $_SERVER['HTTP_HOST'] . $avatarPath;

    $users = new JsonDB('users');
    $oldUser = $users->findOne(fn($u) => $u['id'] === $userId);
    $users->update(fn($u) => $u['id'] === $userId, fn($u) => array_merge($u, ['avatar_url' => $fullUrl]));

    // Delete old avatar
    if ($oldUser && !empty($oldUser['avatar_url']) && strpos($oldUser['avatar_url'], '/avatar/') !== false) {
        $oldName = basename(parse_url($oldUser['avatar_url'], PHP_URL_PATH));
        $oldPath = AVATAR_DIR . '/' . $oldName;
        if (file_exists($oldPath) && strpos(realpath($oldPath), AVATAR_DIR) === 0) @unlink($oldPath);
    }

    $user = $users->findOne(fn($u) => $u['id'] === $userId);
    json_response(['user' => format_user($user, true), 'avatar_url' => $fullUrl]);
}

function handle_search_users(): void
{
    $q = trim($_GET['q'] ?? '');
    if (strlen($q) < 2) error_response('Query too short', 400);
    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(30, max(1, (int)($_GET['limit'] ?? 20)));
    $offset = ($page - 1) * $limit;
    $currentUserId = get_user_id();

    $users = new JsonDB('users');
    $q_lower = mb_strtolower($q);
    $all = $users->read();
    $matches = array_filter($all, function ($u) use ($q_lower) {
        return strpos(mb_strtolower($u['username']), $q_lower) !== false
            || strpos(mb_strtolower($u['display_name'] ?? ''), $q_lower) !== false;
    });
    usort($matches, fn($a, $b) => strcmp($a['username'], $b['username']));
    $total = count($matches);
    $items = array_slice(array_values($matches), $offset, $limit);
    $formatted = array_map(fn($u) => format_user($u), $items);
    json_response(['users' => $formatted, 'page' => $page, 'has_more' => ($offset + $limit) < $total, 'total' => $total]);
}

// ================== FOLLOW ==================
function handle_follow(string $idOrUsername): void
{
    $userId = require_auth();
    $users = new JsonDB('users');
    $target = $users->findOne(fn($u) => $u['username'] === $idOrUsername || $u['id'] === $idOrUsername);
    if (!$target) error_response('User not found', 404);
    if ($target['id'] === $userId) error_response('Cannot follow yourself', 400);

    $follows = new JsonDB('follows');
    if ($follows->findOne(fn($f) => $f['follower_id'] === $userId && $f['following_id'] === $target['id'])) {
        json_response(['success' => true, 'following' => true]);
        return;
    }
    $follows->insert(['follower_id' => $userId, 'following_id' => $target['id']]);

    // Unblock if blocked
    $blocks = new JsonDB('blocks');
    $blocks->delete(fn($b) => $b['blocker_id'] === $userId && $b['blocked_id'] === $target['id']);

    // Notification
    create_notification($target['id'], $userId, 'follow', null);
    json_response(['success' => true, 'following' => true]);
}

function handle_unfollow(string $idOrUsername): void
{
    $userId = require_auth();
    $users = new JsonDB('users');
    $target = $users->findOne(fn($u) => $u['username'] === $idOrUsername || $u['id'] === $idOrUsername);
    if (!$target) error_response('User not found', 404);
    $follows = new JsonDB('follows');
    $follows->delete(fn($f) => $f['follower_id'] === $userId && $f['following_id'] === $target['id']);
    json_response(['success' => true, 'following' => false]);
}

function handle_followers(string $idOrUsername): void
{
    $currentUserId = get_user_id();
    $users = new JsonDB('users');
    $target = $users->findOne(fn($u) => $u['username'] === $idOrUsername || $u['id'] === $idOrUsername);
    if (!$target) error_response('User not found', 404);

    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(50, max(1, (int)($_GET['limit'] ?? 30)));
    $offset = ($page - 1) * $limit;
    $follows = new JsonDB('follows');
    $res = $follows->findAll(fn($f) => $f['following_id'] === $target['id'], 'created_at', true, $offset, $limit);
    $items = [];
    foreach ($res['items'] as $f) {
        $u = $users->findOne(fn($u) => $u['id'] === $f['follower_id']);
        if ($u) {
            $fu = format_user($u);
            if ($currentUserId) {
                $fu['is_following'] = (bool)$follows->findOne(fn($ff) => $ff['follower_id'] === $currentUserId && $ff['following_id'] === $u['id']);
            }
            $items[] = $fu;
        }
    }
    json_response(['users' => $items, 'page' => $page, 'has_more' => count($items) === $limit]);
}

function handle_following(string $idOrUsername): void
{
    $currentUserId = get_user_id();
    $users = new JsonDB('users');
    $target = $users->findOne(fn($u) => $u['username'] === $idOrUsername || $u['id'] === $idOrUsername);
    if (!$target) error_response('User not found', 404);

    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(50, max(1, (int)($_GET['limit'] ?? 30)));
    $offset = ($page - 1) * $limit;
    $follows = new JsonDB('follows');
    $res = $follows->findAll(fn($f) => $f['follower_id'] === $target['id'], 'created_at', true, $offset, $limit);
    $items = [];
    foreach ($res['items'] as $f) {
        $u = $users->findOne(fn($u) => $u['id'] === $f['following_id']);
        if ($u) {
            $fu = format_user($u);
            if ($currentUserId) {
                $fu['is_following'] = (bool)$follows->findOne(fn($ff) => $ff['follower_id'] === $currentUserId && $ff['following_id'] === $u['id']);
            }
            $items[] = $fu;
        }
    }
    json_response(['users' => $items, 'page' => $page, 'has_more' => count($items) === $limit]);
}

// ================== VIDEOS ==================
function handle_upload_video(): void
{
    $userId = require_auth();
    if (!isset($_FILES['file']) || $_FILES['file']['error'] !== UPLOAD_ERR_OK) {
        error_response('Video file required', 400);
    }
    $file = $_FILES['file'];
    if ($file['size'] > MAX_VIDEO_SIZE) error_response('Video too large (max 100MB)', 400);

    $ext = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
    if (!in_array($ext, ['mp4', 'mov', 'webm', 'm4v'])) error_response('Invalid video format. Use mp4, mov, webm, or m4v', 400);

    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime = $finfo->file($file['tmp_name']);
    $allowedMimes = ['video/mp4', 'video/quicktime', 'video/webm', 'video/x-m4v'];
    if (!in_array($mime, $allowedMimes)) error_response('Invalid video file', 400);

    $caption = sanitize_string($_POST['caption'] ?? '', 2000);
    $hashtags = extract_hashtags($caption);
    $duration = (float)($_POST['duration'] ?? 0);

    $vid = bin2hex(random_bytes(12));
    $filename = $vid . '.' . $ext;
    $dest = VIDEO_DIR . '/' . $filename;
    if (strpos(realpath(dirname($dest)), VIDEO_DIR) !== 0) error_response('Invalid path', 400);
    if (!move_uploaded_file($file['tmp_name'], $dest)) error_response('Upload failed', 500);
    chmod($dest, 0644);

    $basePath = rtrim(dirname($_SERVER['SCRIPT_NAME']), '/');
    $videoUrl = (isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off' ? 'https' : 'http') . '://' . $_SERVER['HTTP_HOST'] . $basePath . '/stream/' . $filename;
    $thumbUrl = null;

    $videos = new JsonDB('videos');
    $id = $videos->insert([
        'user_id' => $userId,
        'video_url' => $videoUrl,
        'thumbnail_url' => $thumbUrl,
        'caption' => $caption,
        'hashtags' => array_values($hashtags),
        'duration' => $duration,
        'views' => 0,
    ]);

    $video = $videos->findOne(fn($v) => $v['id'] === $id);
    json_response(['video' => format_video($video, $userId)], 201);
}

function handle_get_video(string $id): void
{
    $currentUserId = get_user_id();
    $videos = new JsonDB('videos');
    $video = $videos->findOne(fn($v) => $v['id'] === $id);
    if (!$video) error_response('Video not found', 404);
    if ($currentUserId && is_blocked($currentUserId, $video['user_id'])) error_response('Video unavailable', 403);
    json_response(['video' => format_video($video, $currentUserId)]);
}

function handle_delete_video(string $id): void
{
    $userId = require_auth();
    $videos = new JsonDB('videos');
    $video = $videos->findOne(fn($v) => $v['id'] === $id);
    if (!$video) error_response('Video not found', 404);
    if ($video['user_id'] !== $userId) error_response('Not authorized', 403);

    // Delete file
    $fname = basename(parse_url($video['video_url'], PHP_URL_PATH));
    $fpath = VIDEO_DIR . '/' . $fname;
    if (file_exists($fpath) && strpos(realpath($fpath), VIDEO_DIR) === 0) @unlink($fpath);

    $videos->delete(fn($v) => $v['id'] === $id);
    // Clean up likes/comments/saves
    $likes = new JsonDB('likes');
    $likes->delete(fn($l) => $l['video_id'] === $id);
    $comments = new JsonDB('comments');
    $comments->delete(fn($c) => $c['video_id'] === $id);
    $saves = new JsonDB('saves');
    $saves->delete(fn($s) => $s['video_id'] === $id);
    $notif = new JsonDB('notifications');
    $notif->delete(fn($n) => ($n['video_id'] ?? '') === $id);

    json_response(['success' => true]);
}

function handle_view_video(string $id): void
{
    $currentUserId = get_user_id(); // views don't require auth
    $videos = new JsonDB('videos');
    $videos->update(
        fn($v) => $v['id'] === $id,
        fn($v) => array_merge($v, ['views' => ($v['views'] ?? 0) + 1])
    );
    json_response(['success' => true]);
}

// ================== FEED ==================
function build_feed(callable $filter, int $page, int $limit, ?string $userId): array
{
    $videos = new JsonDB('videos');
    $blockedIds = $userId ? get_blocked_ids($userId) : [];
    $finalFilter = function ($v) use ($filter, $blockedIds) {
        if (in_array($v['user_id'], $blockedIds)) return false;
        return $filter($v);
    };
    $offset = ($page - 1) * $limit;
    $res = $videos->findAll($finalFilter, 'created_at', true, $offset, $limit + 1);
    $items = array_slice($res['items'], 0, $limit);
    $formatted = array_map(fn($v) => format_video($v, $userId), $items);
    return [
        'videos' => $formatted,
        'page' => $page,
        'has_more' => count($res['items']) > $limit,
    ];
}

function handle_feed_for_you(): void
{
    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(30, max(1, (int)($_GET['limit'] ?? 10)));
    $userId = get_user_id();
    // Simple: return newest videos (can be upgraded with recommendation logic)
    $result = build_feed(fn($v) => true, $page, $limit, $userId);
    json_response($result);
}

function handle_feed_following(): void
{
    $userId = require_auth();
    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(30, max(1, (int)($_GET['limit'] ?? 10)));
    $follows = new JsonDB('follows');
    $following = $follows->findAll(fn($f) => $f['follower_id'] === $userId, 'created_at', true, 0, 2000);
    $followingIds = array_map(fn($f) => $f['following_id'], $following['items']);
    $result = build_feed(fn($v) => in_array($v['user_id'], $followingIds), $page, $limit, $userId);
    json_response($result);
}

// ================== LIKES ==================
function handle_like(string $videoId): void
{
    $userId = require_auth();
    $videos = new JsonDB('videos');
    if (!$videos->findOne(fn($v) => $v['id'] === $videoId)) error_response('Video not found', 404);

    $likes = new JsonDB('likes');
    $existing = $likes->findOne(fn($l) => $l['video_id'] === $videoId && $l['user_id'] === $userId);
    if (!$existing) {
        $likes->insert(['video_id' => $videoId, 'user_id' => $userId]);
        $video = $videos->findOne(fn($v) => $v['id'] === $videoId);
        if ($video && $video['user_id'] !== $userId) {
            create_notification($video['user_id'], $userId, 'like', $videoId);
        }
    }
    $count = $likes->count(fn($l) => $l['video_id'] === $videoId);
    json_response(['liked' => true, 'likes_count' => $count]);
}

function handle_unlike(string $videoId): void
{
    $userId = require_auth();
    $likes = new JsonDB('likes');
    $likes->delete(fn($l) => $l['video_id'] === $videoId && $l['user_id'] === $userId);
    $count = $likes->count(fn($l) => $l['video_id'] === $videoId);
    json_response(['liked' => false, 'likes_count' => $count]);
}

// ================== SAVES ==================
function handle_save(string $videoId): void
{
    $userId = require_auth();
    $videos = new JsonDB('videos');
    if (!$videos->findOne(fn($v) => $v['id'] === $videoId)) error_response('Video not found', 404);
    $saves = new JsonDB('saves');
    if (!$saves->findOne(fn($s) => $s['video_id'] === $videoId && $s['user_id'] === $userId)) {
        $saves->insert(['video_id' => $videoId, 'user_id' => $userId]);
    }
    $count = $saves->count(fn($s) => $s['video_id'] === $videoId);
    json_response(['saved' => true, 'saves_count' => $count]);
}

function handle_unsave(string $videoId): void
{
    $userId = require_auth();
    $saves = new JsonDB('saves');
    $saves->delete(fn($s) => $s['video_id'] === $videoId && $s['user_id'] === $userId);
    $count = $saves->count(fn($s) => $s['video_id'] === $videoId);
    json_response(['saved' => false, 'saves_count' => $count]);
}

function handle_my_saved(): void
{
    $userId = require_auth();
    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(30, max(1, (int)($_GET['limit'] ?? 20)));
    $saves = new JsonDB('saves');
    $res = $saves->findAll(fn($s) => $s['user_id'] === $userId, 'created_at', true, ($page - 1) * $limit, $limit + 1);
    $videos = new JsonDB('videos');
    $items = [];
    $taken = 0;
    foreach ($res['items'] as $s) {
        $v = $videos->findOne(fn($v) => $v['id'] === $s['video_id']);
        if ($v) {
            if ($taken < $limit) {
                $items[] = format_video($v, $userId);
                $taken++;
            }
        }
    }
    json_response(['videos' => $items, 'page' => $page, 'has_more' => count($res['items']) > $limit]);
}

// ================== COMMENTS ==================
function format_comment(array $c, ?string $currentUserId): array
{
    $users = new JsonDB('users');
    $likeDb = new JsonDB('comment_likes');
    $likeCount = $likeDb->count(fn($l) => ($l['comment_id'] ?? '') === $c['id']);
    $author = $users->findOne(fn($u) => $u['id'] === $c['user_id']);
    $out = [
        'id' => $c['id'],
        'video_id' => $c['video_id'],
        'parent_id' => $c['parent_id'] ?? null,
        'user' => $author ? format_user($author) : null,
        'text' => $c['text'],
        'likes_count' => $likeCount,
        'is_liked' => false,
        'reply_count' => 0,
        'created_at' => $c['created_at'],
    ];
    // Count replies
    $comments = new JsonDB('comments');
    $out['reply_count'] = $comments->count(fn($cc) => ($cc['parent_id'] ?? '') === $c['id']);
    if ($currentUserId) {
        $out['is_liked'] = (bool)$likeDb->findOne(fn($l) => ($l['comment_id'] ?? '') === $c['id'] && $l['user_id'] === $currentUserId);
    }
    return $out;
}

function handle_get_comments(string $videoId): void
{
    $currentUserId = get_user_id();
    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(50, max(1, (int)($_GET['limit'] ?? 20)));
    $videos = new JsonDB('videos');
    if (!$videos->findOne(fn($v) => $v['id'] === $videoId)) error_response('Video not found', 404);
    $comments = new JsonDB('comments');
    $res = $comments->findAll(
        fn($c) => $c['video_id'] === $videoId && !isset($c['parent_id']),
        'created_at', false, ($page - 1) * $limit, $limit + 1
    );
    $items = array_slice($res['items'], 0, $limit);
    $formatted = array_map(fn($c) => format_comment($c, $currentUserId), $items);
    json_response(['comments' => $formatted, 'page' => $page, 'has_more' => count($res['items']) > $limit]);
}

function handle_add_comment(string $videoId): void
{
    $userId = require_auth();
    $body = get_json_body();
    $text = sanitize_string($body['text'] ?? '', 1000);
    $parentId = $body['parent_id'] ?? null;
    if (empty($text)) error_response('Comment text required', 400);

    $videos = new JsonDB('videos');
    $video = $videos->findOne(fn($v) => $v['id'] === $videoId);
    if (!$video) error_response('Video not found', 404);

    if ($parentId) {
        $comments = new JsonDB('comments');
        $parent = $comments->findOne(fn($c) => $c['id'] === $parentId && $c['video_id'] === $videoId);
        if (!$parent) error_response('Parent comment not found', 404);
    }

    $comments = new JsonDB('comments');
    $id = $comments->insert([
        'video_id' => $videoId,
        'user_id' => $userId,
        'text' => $text,
        'parent_id' => $parentId,
    ]);
    $comment = $comments->findOne(fn($c) => $c['id'] === $id);

    // Notification
    $notifyUserId = $parentId
        ? ($comments->findOne(fn($c) => $c['id'] === $parentId)['user_id'] ?? null)
        : $video['user_id'];
    if ($notifyUserId && $notifyUserId !== $userId) {
        $type = $parentId ? 'reply' : 'comment';
        create_notification($notifyUserId, $userId, $type, $videoId, $id);
    }

    json_response(['comment' => format_comment($comment, $userId)], 201);
}

function handle_like_comment(string $commentId): void
{
    $userId = require_auth();
    $likeDb = new JsonDB('comment_likes');
    if (!$likeDb->findOne(fn($l) => ($l['comment_id'] ?? '') === $commentId && $l['user_id'] === $userId)) {
        $likeDb->insert(['comment_id' => $commentId, 'user_id' => $userId]);
    }
    $count = $likeDb->count(fn($l) => ($l['comment_id'] ?? '') === $commentId);
    json_response(['liked' => true, 'likes_count' => $count]);
}

function handle_unlike_comment(string $commentId): void
{
    $userId = require_auth();
    $likeDb = new JsonDB('comment_likes');
    $likeDb->delete(fn($l) => ($l['comment_id'] ?? '') === $commentId && $l['user_id'] === $userId);
    $count = $likeDb->count(fn($l) => ($l['comment_id'] ?? '') === $commentId);
    json_response(['liked' => false, 'likes_count' => $count]);
}

function handle_comment_replies(string $commentId): void
{
    $currentUserId = get_user_id();
    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(50, max(1, (int)($_GET['limit'] ?? 20)));
    $comments = new JsonDB('comments');
    $res = $comments->findAll(
        fn($c) => ($c['parent_id'] ?? '') === $commentId,
        'created_at', true, ($page - 1) * $limit, $limit + 1
    );
    $items = array_slice($res['items'], 0, $limit);
    $formatted = array_map(fn($c) => format_comment($c, $currentUserId), $items);
    json_response(['replies' => $formatted, 'page' => $page, 'has_more' => count($res['items']) > $limit]);
}

function handle_delete_comment(string $commentId): void
{
    $userId = require_auth();
    $comments = new JsonDB('comments');
    $comment = $comments->findOne(fn($c) => $c['id'] === $commentId);
    if (!$comment) error_response('Comment not found', 404);

    $videos = new JsonDB('videos');
    $video = $videos->findOne(fn($v) => $v['id'] === $comment['video_id']);
    if ($comment['user_id'] !== $userId && (!$video || $video['user_id'] !== $userId)) {
        error_response('Not authorized', 403);
    }
    $comments->delete(fn($c) => $c['id'] === $commentId || ($c['parent_id'] ?? '') === $commentId);
    $likeDb = new JsonDB('comment_likes');
    $likeDb->delete(fn($l) => ($l['comment_id'] ?? '') === $commentId);
    json_response(['success' => true]);
}

// ================== NOTIFICATIONS ==================
function create_notification(string $toUserId, string $fromUserId, string $type, ?string $videoId = null, ?string $commentId = null): void
{
    $notif = new JsonDB('notifications');
    $notif->insert([
        'user_id' => $toUserId,
        'from_user_id' => $fromUserId,
        'type' => $type,
        'video_id' => $videoId,
        'comment_id' => $commentId,
        'read' => false,
    ]);
}

function format_notification(array $n): array
{
    $users = new JsonDB('users');
    $videos = new JsonDB('videos');
    $from = $users->findOne(fn($u) => $u['id'] === $n['from_user_id']);
    $video = $n['video_id'] ? $videos->findOne(fn($v) => $v['id'] === $n['video_id']) : null;
    return [
        'id' => $n['id'],
        'type' => $n['type'],
        'from_user' => $from ? format_user($from) : null,
        'video' => $video ? format_video($video) : null,
        'read' => (bool)($n['read'] ?? false),
        'created_at' => $n['created_at'],
    ];
}

function handle_notifications(): void
{
    $userId = require_auth();
    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(50, max(1, (int)($_GET['limit'] ?? 30)));
    $notif = new JsonDB('notifications');
    $res = $notif->findAll(
        fn($n) => $n['user_id'] === $userId,
        'created_at', true, ($page - 1) * $limit, $limit + 1
    );
    $items = array_slice($res['items'], 0, $limit);
    $formatted = array_map('format_notification', $items);
    json_response(['notifications' => $formatted, 'page' => $page, 'has_more' => count($res['items']) > $limit]);
}

function handle_mark_notifications_read(): void
{
    $userId = require_auth();
    $notif = new JsonDB('notifications');
    $notif->update(
        fn($n) => $n['user_id'] === $userId && !($n['read'] ?? false),
        fn($n) => array_merge($n, ['read' => true])
    );
    json_response(['success' => true]);
}

// ================== SEARCH & HASHTAGS ==================
function handle_search(): void
{
    $q = trim($_GET['q'] ?? '');
    if (strlen($q) < 1) error_response('Query required', 400);
    $type = $_GET['type'] ?? 'all'; // users|videos|hashtags|all
    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(30, max(1, (int)($_GET['limit'] ?? 20)));
    $userId = get_user_id();

    $result = ['users' => [], 'videos' => [], 'hashtags' => []];
    $q_lower = mb_strtolower($q);
    $tagQuery = ltrim($q, '#');

    if ($type === 'all' || $type === 'users') {
        $users = new JsonDB('users');
        $all = $users->read();
        $matches = array_filter($all, function ($u) use ($q_lower) {
            return strpos(mb_strtolower($u['username']), $q_lower) !== false
                || strpos(mb_strtolower($u['display_name'] ?? ''), $q_lower) !== false;
        });
        usort($matches, fn($a, $b) => strcmp($a['username'], $b['username']));
        $items = array_slice(array_values($matches), 0, $limit);
        $result['users'] = array_map(fn($u) => format_user($u), $items);
    }

    if ($type === 'all' || $type === 'videos') {
        $videos = new JsonDB('videos');
        $blockedIds = $userId ? get_blocked_ids($userId) : [];
        $all = $videos->read();
        $matches = array_filter($all, function ($v) use ($q_lower, $blockedIds) {
            if (in_array($v['user_id'], $blockedIds)) return false;
            return strpos(mb_strtolower($v['caption'] ?? ''), $q_lower) !== false;
        });
        usort($matches, fn($a, $b) => strcmp($b['created_at'], $a['created_at']));
        $items = array_slice(array_values($matches), ($page - 1) * $limit, $limit);
        $result['videos'] = array_map(fn($v) => format_video($v, $userId), $items);
    }

    if ($type === 'all' || $type === 'hashtags') {
        $videos = new JsonDB('videos');
        $all = $videos->read();
        $tagCount = [];
        foreach ($all as $v) {
            foreach (($v['hashtags'] ?? []) as $tag) {
                if (strpos($tag, $tagQuery) !== false) {
                    $tagCount[$tag] = ($tagCount[$tag] ?? 0) + 1;
                }
            }
        }
        arsort($tagCount);
        $tags = [];
        foreach (array_slice($tagCount, 0, $limit, true) as $tag => $count) {
            $tags[] = ['tag' => $tag, 'videos_count' => $count];
        }
        $result['hashtags'] = $tags;
    }
    json_response($result);
}

function handle_trending_hashtags(): void
{
    $videos = new JsonDB('videos');
    $all = $videos->read();
    $tagCount = [];
    foreach ($all as $v) {
        foreach (($v['hashtags'] ?? []) as $tag) {
            $tagCount[$tag] = ($tagCount[$tag] ?? 0) + 1;
        }
    }
    arsort($tagCount);
    $tags = [];
    foreach (array_slice($tagCount, 0, 20, true) as $tag => $count) {
        $tags[] = ['tag' => $tag, 'videos_count' => $count];
    }
    json_response(['hashtags' => $tags]);
}

function handle_hashtag_videos(string $tag): void
{
    $tag = strtolower($tag);
    $page = max(1, (int)($_GET['page'] ?? 1));
    $limit = min(30, max(1, (int)($_GET['limit'] ?? 20)));
    $userId = get_user_id();
    $videos = new JsonDB('videos');
    $blockedIds = $userId ? get_blocked_ids($userId) : [];
    $res = $videos->findAll(
        fn($v) => in_array($tag, $v['hashtags'] ?? []) && !in_array($v['user_id'], $blockedIds),
        'created_at', true, ($page - 1) * $limit, $limit + 1
    );
    $items = array_slice($res['items'], 0, $limit);
    json_response([
        'tag' => $tag,
        'videos' => array_map(fn($v) => format_video($v, $userId), $items),
        'page' => $page,
        'has_more' => count($res['items']) > $limit,
    ]);
}

// ================== REPORTS & BLOCKS ==================
function handle_report(): void
{
    $userId = require_auth();
    $body = get_json_body();
    $reason = sanitize_string($body['reason'] ?? '', 500);
    $videoId = $body['video_id'] ?? null;
    $reportedUserId = $body['user_id'] ?? null;
    if (empty($reason)) error_response('Reason required', 400);

    $reports = new JsonDB('reports');
    $reports->insert([
        'reporter_id' => $userId,
        'video_id' => $videoId,
        'reported_user_id' => $reportedUserId,
        'reason' => $reason,
    ]);
    json_response(['success' => true]);
}

function handle_block(string $idOrUsername): void
{
    $userId = require_auth();
    $users = new JsonDB('users');
    $target = $users->findOne(fn($u) => $u['username'] === $idOrUsername || $u['id'] === $idOrUsername);
    if (!$target) error_response('User not found', 404);
    if ($target['id'] === $userId) error_response('Cannot block yourself', 400);
    $blocks = new JsonDB('blocks');
    if (!$blocks->findOne(fn($b) => $b['blocker_id'] === $userId && $b['blocked_id'] === $target['id'])) {
        $blocks->insert(['blocker_id' => $userId, 'blocked_id' => $target['id']]);
    }
    // Auto-unfollow
    $follows = new JsonDB('follows');
    $follows->delete(fn($f) => $f['follower_id'] === $userId && $f['following_id'] === $target['id']);
    $follows->delete(fn($f) => $f['follower_id'] === $target['id'] && $f['following_id'] === $userId);
    json_response(['success' => true, 'blocked' => true]);
}

function handle_unblock(string $idOrUsername): void
{
    $userId = require_auth();
    $users = new JsonDB('users');
    $target = $users->findOne(fn($u) => $u['username'] === $idOrUsername || $u['id'] === $idOrUsername);
    if (!$target) error_response('User not found', 404);
    $blocks = new JsonDB('blocks');
    $blocks->delete(fn($b) => $b['blocker_id'] === $userId && $b['blocked_id'] === $target['id']);
    json_response(['success' => true, 'blocked' => false]);
}

// ================== STREAMING ==================
function handle_stream(string $filename, string $type): void
{
    if ($type === 'videos') {
        $base = VIDEO_DIR;
    } else {
        $base = AVATAR_DIR;
    }
    // Sanitize filename - no path components allowed
    if (strpos($filename, '/') !== false || strpos($filename, '\\') !== false || strpos($filename, '..') !== false) {
        http_response_code(400);
        exit;
    }
    $path = $base . '/' . $filename;
    $realBase = realpath($base);
    $realPath = realpath($path);
    if (!$realPath || strpos($realPath, $realBase) !== 0 || !is_file($realPath)) {
        http_response_code(404);
        exit;
    }

    $size = filesize($path);
    $ext = strtolower(pathinfo($path, PATHINFO_EXTENSION));
    $mimeTypes = [
        'mp4' => 'video/mp4',
        'm4v' => 'video/mp4',
        'mov' => 'video/quicktime',
        'webm' => 'video/webm',
        'jpg' => 'image/jpeg',
        'jpeg' => 'image/jpeg',
        'png' => 'image/png',
        'webp' => 'image/webp',
    ];
    $mime = $mimeTypes[$ext] ?? 'application/octet-stream';

    // Byte-range support for video seeking
    $start = 0;
    $end = $size - 1;
    $length = $size;
    $status = 200;

    if (isset($_SERVER['HTTP_RANGE'])) {
        $range = $_SERVER['HTTP_RANGE'];
        if (preg_match('/bytes=(\d*)-(\d*)/', $range, $m)) {
            $start = $m[1] === '' ? 0 : (int)$m[1];
            $end = $m[2] === '' ? $size - 1 : (int)$m[2];
            if ($start > $end || $start >= $size) {
                header('HTTP/1.1 416 Requested Range Not Satisfiable');
                header("Content-Range: bytes */$size");
                exit;
            }
            $length = $end - $start + 1;
            $status = 206;
        }
    }

    ob_end_clean();
    http_response_code($status);
    header("Content-Type: $mime");
    header("Accept-Ranges: bytes");
    header("Content-Length: $length");
    header("Content-Disposition: inline; filename=\"$filename\"");
    header('Cache-Control: public, max-age=31536000');
    if ($status === 206) {
        header("Content-Range: bytes $start-$end/$size");
    }

    $fp = fopen($path, 'rb');
    if ($fp) {
        fseek($fp, $start);
        $remaining = $length;
        $chunkSize = 1024 * 256; // 256KB chunks
        while (!feof($fp) && $remaining > 0) {
            $toRead = min($chunkSize, $remaining);
            echo fread($fp, $toRead);
            $remaining -= $toRead;
            flush();
        }
        fclose($fp);
    }
    exit;
}
