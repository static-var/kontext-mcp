import http from 'node:http';

const sendJson = (res, status, body) => {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
};

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (req.method === 'GET' && url.pathname === '/api/session') {
    sendJson(res, 200, { authenticated: true, username: 'demo' });
    return;
  }

  if (req.method === 'POST' && url.pathname === '/login') {
    sendJson(res, 200, { status: 'ok' });
    return;
  }

  if (req.method === 'POST' && url.pathname === '/logout') {
    sendJson(res, 200, { status: 'logged_out' });
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'not_found' }));
});

const PORT = process.env.MOCK_API_PORT ? Number(process.env.MOCK_API_PORT) : 5000;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Mock API listening on http://127.0.0.1:${PORT}`);
});
