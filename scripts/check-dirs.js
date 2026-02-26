import fs from 'fs';
import os from 'os';
import path from 'path';

// Check what directories actually exist
const base = '/vercel/share/v0-project';
console.log('Listing base:', base);
try {
  const items = fs.readdirSync(base);
  console.log('Contents:', items.join(', '));
} catch(e) {
  console.log('Cannot read base:', e.message);
}

// Check MKNotes
const mkn = path.join(base, 'MKNotes');
try {
  const items = fs.readdirSync(mkn);
  console.log('MKNotes contents:', items.join(', '));
} catch(e) {
  console.log('Cannot read MKNotes:', e.message);
}

// Check MKNotes/gradle
const gr = path.join(mkn, 'gradle');
try {
  const items = fs.readdirSync(gr);
  console.log('gradle contents:', items.join(', '));
} catch(e) {
  console.log('Cannot read gradle/:', e.message);
}

// Check MKNotes/gradle/wrapper
const wr = path.join(gr, 'wrapper');
try {
  const items = fs.readdirSync(wr);
  console.log('wrapper contents:', items.join(', '));
} catch(e) {
  console.log('Cannot read wrapper/:', e.message);
}

// Check if /tmp/gradle-wrapper.jar still exists
const tmp = '/tmp/gradle-wrapper.jar';
try {
  const st = fs.statSync(tmp);
  console.log('tmp jar exists, size:', st.size);
} catch(e) {
  console.log('tmp jar gone:', e.message);
}
