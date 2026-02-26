import fs from 'fs';
import path from 'path';
import os from 'os';

const url = 'https://raw.githubusercontent.com/gradle/gradle/v7.5.1/gradle/wrapper/gradle-wrapper.jar';
const tmpDest = path.join(os.tmpdir(), 'gradle-wrapper.jar');

console.log('Fetching:', url);
const resp = await fetch(url);
console.log('Status:', resp.status);

if (resp.ok) {
  const ab = await resp.arrayBuffer();
  const buf = Buffer.from(ab);
  console.log('Size:', buf.length, 'bytes');
  console.log('First 4 hex:', buf.slice(0, 4).toString('hex'));
  console.log('Is valid JAR:', buf[0] === 0x50 && buf[1] === 0x4B);
  fs.writeFileSync(tmpDest, buf);
  console.log('Saved to:', tmpDest);
  
  // Now try to copy to actual location
  const targetDir = '/vercel/share/v0-project/MKNotes/gradle/wrapper';
  const targetFile = path.join(targetDir, 'gradle-wrapper.jar');
  
  try {
    fs.mkdirSync(targetDir, { recursive: true });
  } catch(e) {
    console.log('mkdir error (may already exist):', e.message);
  }
  
  try {
    fs.copyFileSync(tmpDest, targetFile);
    console.log('Copied to:', targetFile);
    const verify = fs.statSync(targetFile);
    console.log('Verified size:', verify.size);
  } catch(e) {
    console.log('Copy error:', e.message);
    console.log('JAR saved at tmp location:', tmpDest);
    // Try writing via base64
    const b64 = buf.toString('base64');
    console.log('BASE64_LENGTH:', b64.length);
    console.log('BASE64_FIRST100:', b64.substring(0, 100));
  }
} else {
  console.log('Failed:', resp.statusText);
}
