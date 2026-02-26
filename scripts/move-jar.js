import { readFileSync, writeFileSync, existsSync } from 'fs';

const src = '/home/user/MKNotes/gradle/wrapper/gradle-wrapper.jar';
const dest = '/vercel/share/v0-project/MKNotes/gradle/wrapper/gradle-wrapper.jar';

if (existsSync(src)) {
  const data = readFileSync(src);
  writeFileSync(dest, data);
  console.log(`Copied ${data.length} bytes to ${dest}`);
  const verify = readFileSync(dest);
  console.log(`Verified: ${verify.length} bytes at destination`);
} else {
  console.log('Source not found at ' + src);
  // Try alternate path
  const alt = '/home/user/MKNotes/gradle/wrapper/gradle-wrapper.jar';
  console.log('Checking alternate: ' + existsSync(alt));
}
