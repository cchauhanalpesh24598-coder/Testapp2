import fs from 'fs';

// Check the current gradle-wrapper.jar
const jarPath = '/vercel/share/v0-project/MKNotes/gradle/wrapper/gradle-wrapper.jar';

if (fs.existsSync(jarPath)) {
  const stat = fs.statSync(jarPath);
  console.log('File size:', stat.size, 'bytes');
  
  const buf = fs.readFileSync(jarPath);
  const hex = buf.slice(0, 16).toString('hex');
  console.log('First 16 bytes (hex):', hex);
  
  // ZIP/JAR files start with PK (50 4B)
  const isValid = buf[0] === 0x50 && buf[1] === 0x4B;
  console.log('Is valid JAR/ZIP:', isValid);
  
  if (!isValid) {
    console.log('First 200 chars as text:', buf.slice(0, 200).toString('utf8'));
  }
} else {
  console.log('gradle-wrapper.jar does NOT exist');
}
