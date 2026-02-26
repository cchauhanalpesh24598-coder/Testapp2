import https from 'https';
import http from 'http';
import fs from 'fs';

const destPath = '/vercel/share/v0-project/scripts/gradle-wrapper.jar';

function download(url) {
  return new Promise((resolve, reject) => {
    const proto = url.startsWith('https') ? https : http;
    proto.get(url, { headers: { 'User-Agent': 'Mozilla/5.0' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        download(res.headers.location).then(resolve).catch(reject);
        return;
      }
      if (res.statusCode !== 200) {
        reject(new Error(`HTTP ${res.statusCode}`));
        return;
      }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
      res.on('error', reject);
    }).on('error', reject);
  });
}

async function main() {
  const urls = [
    'https://raw.githubusercontent.com/nickclearyinvest/gradle-wrapper-jar/main/gradle-7.5.1/gradle-wrapper.jar',
    'https://raw.githubusercontent.com/nickclearyinvest/gradle-wrapper-jar/main/gradle-7.5/gradle-wrapper.jar',
    'https://raw.githubusercontent.com/nickclearyinvest/gradle-wrapper-jar/main/gradle-7.4.2/gradle-wrapper.jar',
  ];

  for (const url of urls) {
    try {
      console.log('Trying:', url);
      const buf = await download(url);
      if (buf.length > 50000) {
        fs.writeFileSync(destPath, buf);
        console.log('SUCCESS: Downloaded gradle-wrapper.jar (' + buf.length + ' bytes) to scripts/');
        return;
      }
      console.log('Too small:', buf.length, 'bytes, trying next...');
    } catch (e) {
      console.log('Failed:', e.message, '- trying next...');
    }
  }
  console.log('Could not download from any source');
  
  // Check if current jar in project is valid
  const currentJar = '/vercel/share/v0-project/MKNotes/gradle/wrapper/gradle-wrapper.jar';
  if (fs.existsSync(currentJar)) {
    const head = Buffer.alloc(4);
    const fd = fs.openSync(currentJar, 'r');
    fs.readSync(fd, head, 0, 4, 0);
    fs.closeSync(fd);
    const isJar = head[0] === 0x50 && head[1] === 0x4B;
    const size = fs.statSync(currentJar).size;
    console.log('Current jar: size=' + size + ', valid_zip=' + isJar);
  }
}

main().catch(e => {
  console.error('Fatal:', e.message);
  process.exit(1);
});
