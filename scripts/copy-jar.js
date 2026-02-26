import { copyFileSync, existsSync, statSync } from 'fs';

const src = '/tmp/gradle-wrapper.jar';
const dest = '/vercel/share/v0-project/MKNotes/gradle/wrapper/gradle-wrapper.jar';

if (existsSync(src)) {
    copyFileSync(src, dest);
    const stat = statSync(dest);
    console.log(`Copied gradle-wrapper.jar to project. Size: ${stat.size} bytes`);
    console.log('SUCCESS!');
} else {
    console.error('Source file not found at /tmp');
}
