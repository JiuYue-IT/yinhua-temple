"""Static preview with byte-range support for scroll-controlled videos.

Also proxies /api/* to the Spring backend (default http://127.0.0.1:8080) so the
pages can call the API with same-origin relative paths, exactly as they would
when the backend hosts this folder itself (WEB_DIST).
"""
import argparse
import json
import os
import re
import urllib.error
import urllib.request
import webbrowser
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

BACKEND = 'http://127.0.0.1:8080'


class PreviewHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith('/api/'):
            return self.proxy('GET')
        return super().do_GET()

    def do_POST(self):
        if self.path.startswith('/api/'):
            return self.proxy('POST')
        self.send_error(405)

    def proxy(self, method):
        length = int(self.headers.get('Content-Length') or 0)
        body = self.rfile.read(length) if length else None
        req = urllib.request.Request(BACKEND + self.path, data=body, method=method)
        if body is not None:
            req.add_header('Content-Type', self.headers.get('Content-Type', 'application/json'))
        try:
            with urllib.request.urlopen(req, timeout=90) as res:
                status, payload = res.status, res.read()
                content_type = res.headers.get('Content-Type', 'application/json')
        except urllib.error.HTTPError as err:
            status, payload = err.code, err.read()
            content_type = err.headers.get('Content-Type', 'application/json')
        except (urllib.error.URLError, OSError) as err:
            status = 502
            content_type = 'application/json;charset=UTF-8'
            payload = json.dumps({'error': {'code': 'BACKEND_DOWN',
                                            'message': f'连不上后端 {BACKEND}，请先启动 server（{err.reason if hasattr(err, "reason") else err}）'}},
                                 ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        try:
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass

    def log_message(self, fmt, *args):
        if not str(args[0]).startswith(('GET /assets', 'GET /gate/assets', 'GET /daxiong-baodian/assets',
                                        'GET /wishing-pool/assets', 'GET /bodhi-fruit/assets')):
            super().log_message(fmt, *args)

    def send_head(self):
        self.byte_range = None
        header = self.headers.get('Range')
        path = self.translate_path(self.path)
        if not header or not os.path.isfile(path):
            return super().send_head()
        match = re.fullmatch(r'bytes=(\d*)-(\d*)', header.strip())
        if not match or not any(match.groups()):
            return super().send_head()
        try:
            stream = open(path, 'rb')
        except OSError:
            self.send_error(404)
            return None
        stat = os.fstat(stream.fileno())
        size = stat.st_size
        first, last = match.groups()
        if first:
            start = int(first)
            end = min(int(last) if last else size - 1, size - 1)
        else:
            start = max(0, size - int(last))
            end = size - 1
        if start >= size or end < start:
            stream.close()
            self.send_response(416)
            self.send_header('Content-Range', f'bytes */{size}')
            self.send_header('Content-Length', '0')
            self.end_headers()
            return None
        self.byte_range = (start, end)
        stream.seek(start)
        self.send_response(206)
        self.send_header('Content-Type', self.guess_type(path))
        self.send_header('Content-Range', f'bytes {start}-{end}/{size}')
        self.send_header('Content-Length', str(end - start + 1))
        self.send_header('Last-Modified', self.date_time_string(stat.st_mtime))
        self.end_headers()
        return stream

    def end_headers(self):
        self.send_header('Accept-Ranges', 'bytes')
        self.send_header('Cache-Control', 'no-cache')
        super().end_headers()

    def copyfile(self, source, outputfile):
        try:
            if self.byte_range is None:
                return super().copyfile(source, outputfile)
            remaining = self.byte_range[1] - self.byte_range[0] + 1
            while remaining > 0:
                chunk = source.read(min(256 * 1024, remaining))
                if not chunk:
                    break
                outputfile.write(chunk)
                remaining -= len(chunk)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=0)
    parser.add_argument('--no-open', action='store_true')
    parser.add_argument('--backend', default=BACKEND, help='人生支线后端地址，/api 会转发到这里')
    args = parser.parse_args()
    BACKEND = args.backend.rstrip('/')
    root = str(Path(__file__).resolve().parent)
    server = ThreadingHTTPServer(('127.0.0.1', args.port), partial(PreviewHandler, directory=root))
    url = f'http://127.0.0.1:{server.server_port}/'
    print(f'Preview: {url}   (/api -> {BACKEND})', flush=True)
    if not args.no_open:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()
