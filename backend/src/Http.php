<?php
declare(strict_types=1);

namespace Gpa;

/** Request/response value objects so the app can be driven by HTTP or by tests in-process. */
final class Request
{
    public string $method;
    public string $path;
    public array $headers;
    public array $query;
    public array $body;
    public string $ip;

    public function __construct(string $method, string $path, array $headers, array $query, array $body, string $ip = '')
    {
        $this->method = strtoupper($method);
        $this->path = '/' . trim($path, '/');
        $this->headers = [];
        foreach ($headers as $k => $v) $this->headers[strtolower((string)$k)] = $v;
        $this->query = $query;
        $this->body = $body;
        $this->ip = $ip;
    }

    public function header(string $name, string $default = ''): string
    {
        return (string)($this->headers[strtolower($name)] ?? $default);
    }

    public function bearer(): string
    {
        $h = $this->header('Authorization');
        return stripos($h, 'Bearer ') === 0 ? trim(substr($h, 7)) : '';
    }

    public function str(string $key, string $default = ''): string
    {
        $v = $this->body[$key] ?? $default;
        return is_scalar($v) ? (string)$v : $default;
    }

    public function int(string $key, int $default = 0): int
    {
        $v = $this->body[$key] ?? $default;
        return is_numeric($v) ? (int)$v : $default;
    }

    public static function fromGlobals(string $basePath): self
    {
        $uri = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
        $path = $uri;
        if ($basePath !== '' && $basePath !== '/' && strpos($path, $basePath) === 0) {
            $path = substr($path, strlen($basePath));
        }
        if (isset($_GET['route'])) $path = (string)$_GET['route']; // fallback when mod_rewrite is off
        $headers = function_exists('getallheaders') ? getallheaders() : [];
        foreach ($_SERVER as $k => $v) {
            if (strpos($k, 'HTTP_') === 0) $headers[str_replace('_', '-', substr($k, 5))] = $v;
        }
        $raw = file_get_contents('php://input') ?: '';
        $body = [];
        if ($raw !== '') {
            $decoded = json_decode($raw, true);
            if (is_array($decoded)) $body = $decoded;
        }
        if (!$body && !empty($_POST)) $body = $_POST;
        $ip = $_SERVER['HTTP_CF_CONNECTING_IP'] ?? $_SERVER['HTTP_X_FORWARDED_FOR'] ?? $_SERVER['REMOTE_ADDR'] ?? '';
        $ip = trim(explode(',', (string)$ip)[0]);
        return new self($_SERVER['REQUEST_METHOD'] ?? 'GET', $path, $headers, $_GET, $body, $ip);
    }
}

final class Response
{
    public int $status;
    public array $payload;

    public function __construct(int $status, array $payload)
    {
        $this->status = $status;
        $this->payload = $payload;
    }

    public static function ok($data = null): self
    {
        return new self(200, ['ok' => true, 'data' => $data]);
    }

    public static function error(int $status, string $message): self
    {
        return new self($status, ['ok' => false, 'error' => $message]);
    }

    public function send(array $allowedOrigins = []): void
    {
        http_response_code($this->status);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        $origin = $_SERVER['HTTP_ORIGIN'] ?? '';
        if ($origin !== '' && in_array($origin, $allowedOrigins, true)) {
            header('Access-Control-Allow-Origin: ' . $origin);
            header('Access-Control-Allow-Headers: Content-Type, Authorization, X-App-Key, X-Device-Id, X-App-Version');
            header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
            header('Vary: Origin');
        }
        echo json_encode($this->payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    }
}

final class HttpException extends \RuntimeException
{
    public int $status;

    public function __construct(int $status, string $message)
    {
        parent::__construct($message);
        $this->status = $status;
    }
}
