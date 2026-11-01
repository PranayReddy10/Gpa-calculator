<?php
declare(strict_types=1);

namespace Gpa\Controllers;

use Gpa\AppConfig;
use Gpa\Auth;
use Gpa\HttpException;
use Gpa\Request;
use Gpa\Response;

/**
 * In-app shop. The app never talks to the shop API directly: this server calls
 * {base_url}/api/v1/products and /categories with the shop's API key (stored in the admin
 * config, never sent to the app), trims the data to what the app shows and caches it.
 */
final class ShopController
{
    /** Cache file; tests point it somewhere else. */
    public static string $cacheFile = __DIR__ . '/../../data/shop-cache.json';

    /**
     * HTTP GET used for the shop API: fn(string $url, string $apiKey): array{0:int,1:string}.
     * Tests replace it with a fake.
     * @var callable|null
     */
    public static $fetcher = null;

    private const MAX_PAGES = 20;

    /** GET /api/v1/shop — app key. Catalogue for the shop screens. */
    public static function catalog(Request $r): Response
    {
        Auth::requireAppKey($r);
        $shop = AppConfig::load()['shop'];
        if (!$shop['enabled'] || trim($shop['api_key']) === '') throw new HttpException(404, 'Shop is turned off');
        return Response::ok(self::get($shop, false));
    }

    /** POST /api/v1/admin/shop/test — admin. Checks URL + key (from the body, or the saved config). */
    public static function test(Request $r): Response
    {
        Auth::requireAdmin($r);
        $shop = AppConfig::load()['shop'];
        $b = $r->body;
        if (isset($b['base_url']) && is_string($b['base_url']) && $b['base_url'] !== '') $shop['base_url'] = trim($b['base_url']);
        if (isset($b['api_key']) && is_string($b['api_key']) && $b['api_key'] !== '') $shop['api_key'] = trim($b['api_key']);
        if (!preg_match('#^https://[a-z0-9.-]+(:\d+)?/?$#i', $shop['base_url'])) throw new HttpException(422, 'Shop URL must look like https://madeforu.co.in');
        [$status, $body] = self::fetch(self::api($shop, 'products?per_page=1'), $shop['api_key']);
        $json = json_decode($body, true);
        if ($status === 401 || $status === 403) throw new HttpException(422, 'The shop rejected the API key (HTTP ' . $status . '). Copy it again from the shop admin.');
        if ($status !== 200 || !is_array($json) || !isset($json['data'])) {
            throw new HttpException(502, 'Could not read products from ' . $shop['base_url'] . ' (HTTP ' . $status . ')' . ($status === 0 ? ': ' . $body : ''));
        }
        $first = $json['data'][0]['name'] ?? '';
        return Response::ok(['ok' => true, 'products' => (int)($json['meta']['total'] ?? count($json['data'])), 'sample' => (string)$first]);
    }

    /** POST /api/v1/admin/shop/refresh — admin. Drops the cache and loads the catalogue again. */
    public static function refresh(Request $r): Response
    {
        Auth::requireAdmin($r);
        $shop = AppConfig::load()['shop'];
        if (trim($shop['api_key']) === '') throw new HttpException(422, 'Add the shop API key first');
        $data = self::get($shop, true);
        return Response::ok([
            'products' => count($data['products']),
            'categories' => count($data['categories']),
            'fetched_at' => $data['fetched_at'],
            'stale' => $data['stale'],
        ]);
    }

    // ------------------------------------------------------------------ internals

    /** Cached catalogue, refetched when older than cache_minutes; on failure the last good copy is served. */
    public static function get(array $shop, bool $force): array
    {
        $key = sha1($shop['base_url'] . "\n" . $shop['api_key']);
        $cached = self::readCache();
        $fresh = $cached && $cached['key'] === $key && time() - $cached['fetched_at'] < $shop['cache_minutes'] * 60;
        if ($fresh && !$force) return self::present($shop, $cached);

        try {
            $catalog = self::load($shop);
            $entry = ['key' => $key, 'fetched_at' => time()] + $catalog;
            self::writeCache($entry);
            return self::present($shop, $entry);
        } catch (HttpException $e) {
            // Shop down or slow: keep the app's shop working with the last good copy.
            if ($cached && $cached['key'] === $key) return self::present($shop, $cached, true);
            throw $e;
        }
    }

    private static function present(array $shop, array $entry, bool $stale = false): array
    {
        return [
            'name' => $shop['name'],
            'title' => $shop['title'],
            'subtitle' => $shop['subtitle'],
            'base_url' => rtrim($shop['base_url'], '/'),
            'checkout_in_app' => (bool)$shop['checkout_in_app'],
            'fetched_at' => $entry['fetched_at'],
            'stale' => $stale,
            'categories' => $entry['categories'],
            'products' => array_map(static function (array $p) use ($shop): array {
                $p['url'] = self::withUtm($p['url'], $shop['utm']);
                foreach ($p['variants'] as &$v) $v['url'] = self::withUtm($v['url'], $shop['utm']);
                return $p;
            }, $entry['products']),
        ];
    }

    /** @return array{products: array, categories: array} */
    private static function load(array $shop): array
    {
        $products = [];
        for ($page = 1; $page <= self::MAX_PAGES; $page++) {
            $json = self::getJson($shop, 'products?per_page=50&page=' . $page);
            foreach ((array)($json['data'] ?? []) as $p) {
                if (is_array($p) && isset($p['id'], $p['name'])) $products[] = self::product($p);
            }
            $last = (int)($json['meta']['last_page'] ?? 1);
            if ($page >= $last || empty($json['data'])) break;
        }
        // Categories: a product in "Square magnets" also belongs to its parent "Magnets", so the
        // app can filter by top-level categories. Categories without products (e.g. internal
        // "Raw materials") are hidden.
        $cats = [];
        foreach ((array)(self::getJson($shop, 'categories')['data'] ?? []) as $c) {
            if (is_array($c) && isset($c['slug'], $c['name'])) $cats[(string)$c['slug']] = $c;
        }
        $slugById = [];
        foreach ($cats as $slug => $c) $slugById[(int)($c['id'] ?? 0)] = $slug;
        $parentOf = static function (string $slug) use ($cats, $slugById): ?string {
            $pid = (int)($cats[$slug]['parent_id'] ?? 0);
            return $pid && isset($slugById[$pid]) && $slugById[$pid] !== $slug ? $slugById[$pid] : null;
        };
        $members = [];
        foreach ($products as &$prod) {
            $all = [];
            foreach ($prod['category_slugs'] as $slug) {
                for ($s = $slug, $guard = 0; $s !== null && $guard < 10; $s = $parentOf($s), $guard++) $all[$s] = true;
            }
            $prod['category_slugs'] = array_keys($all);
            foreach ($all as $s => $_) $members[$s][$prod['id']] = true;
        }
        unset($prod);
        $categories = [];
        foreach ($cats as $slug => $c) {
            if (empty($members[$slug])) continue;
            $parent = $parentOf($slug);
            $categories[] = [
                'id' => (int)($c['id'] ?? 0),
                'name' => self::text((string)$c['name'], 60),
                'slug' => $slug,
                'parent' => $parent ?? '',
                'image' => self::url($c['image'] ?? ''),
                'count' => count($members[$slug]),
            ];
        }
        return ['products' => $products, 'categories' => $categories];
    }

    /** Keeps what the app shows; drops SEO blocks and HTML. */
    private static function product(array $p): array
    {
        $images = [];
        foreach ((array)($p['images'] ?? []) as $im) {
            $u = self::url(is_array($im) ? ($im['url'] ?? '') : $im);
            if ($u !== '' && !in_array($u, $images, true)) $images[] = $u;
        }
        $main = self::url($p['image'] ?? '');
        if ($main !== '' && !in_array($main, $images, true)) array_unshift($images, $main);

        $variants = [];
        foreach ((array)($p['variants'] ?? []) as $v) {
            if (!is_array($v)) continue;
            $opts = [];
            foreach ((array)($v['options'] ?? []) as $k => $val) $opts[self::text((string)$k, 40)] = self::text((string)$val, 60);
            $variants[] = [
                'id' => (int)($v['id'] ?? 0),
                'options' => (object)$opts,
                'price' => (float)($v['price'] ?? 0),
                'regular_price' => (float)($v['regular_price'] ?? ($v['price'] ?? 0)),
                'in_stock' => (bool)($v['in_stock'] ?? true),
                'image' => self::url($v['image'] ?? ''),
                'url' => self::url($v['url'] ?? ''),
            ];
        }

        $personal = [];
        foreach ((array)($p['personalisation'] ?? []) as $x) {
            if (!is_array($x)) continue;
            $personal[] = [
                'label' => self::text((string)($x['label'] ?? ''), 80),
                'type' => (string)($x['type'] ?? ''),
                'required' => (bool)($x['required'] ?? false),
                'help' => self::text((string)($x['help'] ?? ''), 200),
                'max_files' => (int)($x['max_files'] ?? 0),
                'max_length' => (int)($x['max_length'] ?? 0),
                'options' => array_values(array_map(static fn($o) => self::text(is_array($o) ? (string)($o['label'] ?? $o['value'] ?? '') : (string)$o, 60), (array)($x['options'] ?? []))),
            ];
        }

        $attrs = [];
        foreach ((array)($p['attributes'] ?? []) as $k => $vals) {
            $attrs[self::text((string)$k, 40)] = array_values(array_map(static fn($v) => self::text((string)$v, 60), (array)$vals));
        }

        return [
            'id' => (int)$p['id'],
            'sku' => (string)($p['sku'] ?? ''),
            'name' => self::text((string)$p['name'], 160),
            'slug' => (string)($p['slug'] ?? ''),
            'url' => self::url($p['url'] ?? ''),
            'type' => (string)($p['type'] ?? 'simple'),
            'price' => (float)($p['price'] ?? 0),
            'regular_price' => (float)($p['regular_price'] ?? ($p['price'] ?? 0)),
            'max_price' => (float)($p['max_price'] ?? ($p['price'] ?? 0)),
            'on_sale' => (bool)($p['on_sale'] ?? false),
            'discount_percent' => (int)($p['discount_percent'] ?? 0),
            'currency' => (string)($p['currency'] ?? 'INR'),
            'in_stock' => (bool)($p['in_stock'] ?? true),
            'featured' => (bool)($p['featured'] ?? false),
            'image' => $images[0] ?? '',
            'thumbnail' => self::url($p['thumbnail'] ?? '') ?: ($images[0] ?? ''),
            'images' => array_slice($images, 0, 12),
            'category' => self::text((string)($p['category'] ?? ''), 60),
            'category_slugs' => array_values(array_filter(array_map(static fn($c) => is_array($c) ? (string)($c['slug'] ?? '') : '', (array)($p['categories'] ?? [])))),
            'short' => self::text(strip_tags(html_entity_decode((string)($p['short_description'] ?? ''), ENT_QUOTES | ENT_HTML5, 'UTF-8')), 300),
            'description' => self::paragraphs((string)($p['description_text'] ?? strip_tags((string)($p['description_html'] ?? ''))), 4000),
            'attributes' => (object)$attrs,
            'variants' => $variants,
            'personalisation' => $personal,
            'production_days' => (int)($p['production_days'] ?? 0),
            'rating' => [
                'average' => (float)($p['rating']['average'] ?? 0),
                'count' => (int)($p['rating']['count'] ?? 0),
            ],
        ];
    }

    private static function getJson(array $shop, string $path): array
    {
        [$status, $body] = self::fetch(self::api($shop, $path), $shop['api_key']);
        $json = json_decode($body, true);
        if ($status !== 200 || !is_array($json)) {
            throw new HttpException(502, 'Shop API ' . $path . ' failed (HTTP ' . $status . ')');
        }
        return $json;
    }

    private static function api(array $shop, string $path): string
    {
        return rtrim($shop['base_url'], '/') . '/api/v1/' . $path;
    }

    /** @return array{0:int,1:string} */
    private static function fetch(string $url, string $apiKey): array
    {
        if (self::$fetcher) return (self::$fetcher)($url, $apiKey);
        $headers = ['Accept: application/json', 'X-Api-Key: ' . $apiKey, 'User-Agent: GPA-Calculator-Server/1.0'];
        if (function_exists('curl_init')) {
            $ch = curl_init($url);
            curl_setopt_array($ch, [
                CURLOPT_HTTPHEADER => $headers,
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_CONNECTTIMEOUT => 8,
                CURLOPT_TIMEOUT => 20,
                CURLOPT_FOLLOWLOCATION => false,
            ]);
            $resp = curl_exec($ch);
            $status = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
            $err = curl_error($ch);
            curl_close($ch);
            return [$resp === false ? 0 : $status, $resp === false ? $err : (string)$resp];
        }
        $ctx = stream_context_create(['http' => ['header' => implode("\r\n", $headers), 'timeout' => 20, 'ignore_errors' => true, 'follow_location' => 0]]);
        $resp = @file_get_contents($url, false, $ctx);
        $status = 0;
        foreach ($http_response_header ?? [] as $h) if (preg_match('#^HTTP/\S+\s+(\d{3})#', $h, $m)) $status = (int)$m[1];
        return [$resp === false ? 0 : $status, $resp === false ? 'request failed' : $resp];
    }

    private static function readCache(): ?array
    {
        if (!is_file(self::$cacheFile)) return null;
        $d = json_decode((string)@file_get_contents(self::$cacheFile), true);
        return is_array($d) && isset($d['key'], $d['fetched_at'], $d['products'], $d['categories']) ? $d : null;
    }

    private static function writeCache(array $entry): void
    {
        $tmp = self::$cacheFile . '.' . getmypid() . '.tmp';
        if (@file_put_contents($tmp, json_encode($entry, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)) !== false) {
            @rename($tmp, self::$cacheFile);
        }
    }

    private static function withUtm(string $url, string $utm): string
    {
        if ($url === '' || $utm === '') return $url;
        return $url . (strpos($url, '?') === false ? '?' : '&') . $utm;
    }

    /** Only http(s) URLs reach the app. */
    private static function url($u): string
    {
        $u = is_string($u) ? trim($u) : '';
        return preg_match('#^https?://#i', $u) ? $u : '';
    }

    private static function text(string $s, int $max): string
    {
        $s = trim(preg_replace('/\s+/u', ' ', html_entity_decode($s, ENT_QUOTES | ENT_HTML5, 'UTF-8')) ?? '');
        return self::cut($s, $max);
    }

    /** Like text() but keeps paragraph breaks (single blank line between paragraphs). */
    private static function paragraphs(string $s, int $max): string
    {
        $s = html_entity_decode(str_replace("\r", '', $s), ENT_QUOTES | ENT_HTML5, 'UTF-8');
        $lines = array_map(static fn($l) => trim(preg_replace('/[ \t\x{00A0}]+/u', ' ', $l) ?? ''), explode("\n", $s));
        $s = trim(preg_replace("/\n{3,}/", "\n\n", implode("\n", $lines)) ?? '');
        return self::cut($s, $max);
    }

    private static function cut(string $s, int $max): string
    {
        if (function_exists('mb_strlen')) {
            return mb_strlen($s) > $max ? rtrim(mb_substr($s, 0, $max - 1)) . '…' : $s;
        }
        if (strlen($s) <= $max) return $s;
        preg_match('/^.{0,' . ($max - 1) . '}/us', $s, $m); // UTF-8 safe without mbstring
        return rtrim($m[0] ?? '') . '…';
    }
}
