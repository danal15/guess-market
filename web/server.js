'use strict';

/**
 * Serves the web client, and passes its calls on to Guess Market.
 *
 * The reason this exists at all is the browser. A page served from one address
 * may not call another one: the request is blocked before it is sent, and the
 * session cookie would not travel with it either. The usual answer is to let
 * the server say it permits it, but exercise 4 forbids touching how the server
 * is set up - it has to be the very war from exercise 3, deployed exactly as
 * before. So the page and the market are put behind one address instead: this
 * serves the files, and anything under the market's own path it forwards to
 * Tomcat and hands the answer back. As far as the browser is concerned there is
 * only ever one origin, and nothing about the server changed.
 *
 * It uses nothing but Node itself. Installing anything would need the internet,
 * and a submission that cannot be marked on a machine without it is not worth
 * the convenience.
 */

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const PORT = Number(process.env.PORT) || 3000;
const MARKET = process.env.MARKET || 'http://localhost:8080';
const CONTEXT = '/guess-market';

const PUBLIC_DIR = path.join(__dirname, 'public');

const TYPES = {
  '.html': 'text/html; charset=UTF-8',
  '.css': 'text/css; charset=UTF-8',
  '.js': 'text/javascript; charset=UTF-8',
  '.json': 'application/json; charset=UTF-8',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

const market = new URL(MARKET);

/** Everything under the market's own path goes to Tomcat; the rest is a file. */
function isForMarket(url) {
  return url === CONTEXT || url.startsWith(CONTEXT + '/');
}

function proxy(request, response) {
  const forwarded = http.request(
    {
      hostname: market.hostname,
      port: market.port || 80,
      path: request.url,
      method: request.method,
      headers: { ...request.headers, host: market.host },
    },
    (answer) => {
      response.writeHead(answer.statusCode, answer.headers);
      answer.pipe(response);
    }
  );

  forwarded.on('error', (error) => {
    // Said in the same shape the servlets use, so the page has one way of
    // reading a refusal whether it came from the market or from here.
    response.writeHead(502, { 'Content-Type': 'application/json; charset=UTF-8' });
    response.end(
      JSON.stringify({
        type: 'ServerUnreachable',
        message:
          'The Guess Market server at ' + MARKET + ' could not be reached. ' +
          'Start Tomcat with guess-market.war deployed, then try again. (' +
          error.code + ')',
      })
    );
  });

  request.pipe(forwarded);
}

function serveFile(request, response) {
  const asked = decodeURIComponent(new URL(request.url, 'http://x').pathname);
  const wanted = asked === '/' ? '/index.html' : asked;

  // Resolved and then checked, so a path with ".." in it cannot climb out of
  // the folder that is meant to be public.
  const file = path.join(PUBLIC_DIR, wanted);
  if (!file.startsWith(PUBLIC_DIR)) {
    response.writeHead(403).end('Forbidden');
    return;
  }

  fs.readFile(file, (error, body) => {
    if (error) {
      response.writeHead(404, { 'Content-Type': 'text/plain; charset=UTF-8' });
      response.end('Not found: ' + wanted);
      return;
    }
    response.writeHead(200, {
      'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream',
      // The marker will be reloading as they click about; a cached old script
      // would have them looking at yesterday's page.
      'Cache-Control': 'no-store',
    });
    response.end(body);
  });
}

http
  .createServer((request, response) => {
    if (isForMarket(request.url)) {
      proxy(request, response);
    } else {
      serveFile(request, response);
    }
  })
  .listen(PORT, () => {
    console.log('');
    console.log('  Guess Market - web client');
    console.log('  ---------------------------------------------------------');
    console.log('  Open this in a browser:   http://localhost:' + PORT);
    console.log('  Talking to the market at: ' + MARKET + CONTEXT);
    console.log('');
    console.log('  Leave this window open. Press Ctrl+C to stop.');
    console.log('');
  })
  .on('error', (error) => {
    if (error.code === 'EADDRINUSE') {
      console.error('Port ' + PORT + ' is already in use.');
      console.error('Close whatever is using it, or pick another port first:');
      console.error('  set PORT=' + (PORT + 1) + '  and run run-web.bat again.');
    } else {
      console.error(String(error));
    }
    process.exit(1);
  });
