const url = 'https://raw.githubusercontent.com/gradle/gradle/v7.5.1/gradle/wrapper/gradle-wrapper.jar';
console.log('Fetching gradle-wrapper.jar...');
const resp = await fetch(url);
if (!resp.ok) {
  console.log('FAILED:', resp.status, resp.statusText);
  process.exit(1);
}
const ab = await resp.arrayBuffer();
const buf = Buffer.from(ab);
console.log('Size:', buf.length, 'bytes');
console.log('Valid JAR:', buf[0] === 0x50 && buf[1] === 0x4B);

// Output as base64 for manual placement
console.log('---BASE64_START---');
console.log(buf.toString('base64'));
console.log('---BASE64_END---');
