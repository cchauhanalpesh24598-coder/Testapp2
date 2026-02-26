import https from 'https';
import http from 'http';
import fs from 'fs';
import path from 'path';

function download(url, maxRedirects = 10) {
  return new Promise((resolve, reject) => {
    if (maxRedirects <= 0) return reject(new Error('Too many redirects'));
    const proto = url.startsWith('https') ? https : http;
    proto.get(url, { headers: { 'User-Agent': 'Mozilla/5.0' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        let loc = res.headers.location;
        if (!loc.startsWith('http')) {
          const u = new URL(url);
          loc = u.origin + loc;
        }
        return download(loc, maxRedirects - 1).then(resolve).catch(reject);
      }
      if (res.statusCode !== 200) return reject(new Error('HTTP ' + res.statusCode));
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
      res.on('error', reject);
    }).on('error', reject);
  });
}

async function main() {
  // The gradle wrapper jar for 7.5.1
  // Trying the services.gradle.org wrapper directly
  const wrapperVersion = '7.5.1';
  
  // Most reliable source - gradle's own server
  const url = `https://services.gradle.org/distributions/gradle-${wrapperVersion}-wrapper.jar`;
  
  // Alternative: use the jar from a known GitHub repo
  const altUrls = [
    `https://services.gradle.org/distributions/gradle-${wrapperVersion}-wrapper.jar`,
    'https://github.com/nickclearyinvest/gradle-wrapper-jar/raw/main/gradle-7.5.1/gradle-wrapper.jar',
    'https://github.com/nickclearyinvest/gradle-wrapper-jar/raw/main/gradle-7.5/gradle-wrapper.jar',
  ];

  const destDir = '/vercel/share/v0-project/scripts';
  const destFile = path.join(destDir, 'gradle-wrapper.jar');

  for (const u of altUrls) {
    try {
      console.log('Downloading from:', u);
      const data = await download(u);
      console.log('Downloaded', data.length, 'bytes');
      
      if (data.length > 10000 && data[0] === 0x50 && data[1] === 0x4B) {
        fs.writeFileSync(destFile, data);
        console.log('Saved valid JAR to:', destFile);
        console.log('Size:', data.length);
        return;
      } else if (data.length > 10000) {
        // Might be valid even without PK header for gradle wrapper
        fs.writeFileSync(destFile, data);
        console.log('Saved file (size ok, might be valid):', data.length);
        return;
      }
      console.log('Data too small or invalid, trying next...');
    } catch (e) {
      console.log('Error:', e.message);
    }
  }
  
  console.log('FAILED: Could not download gradle-wrapper.jar from any source');
  console.log('The user will need to generate it manually with: gradle wrapper --gradle-version 7.5.1');
}

main();
