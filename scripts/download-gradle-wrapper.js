import { writeFileSync, mkdirSync, existsSync, statSync } from 'fs';
import { join } from 'path';

const destDir = join(process.cwd(), 'MKNotes', 'gradle', 'wrapper');
const destPath = join(destDir, 'gradle-wrapper.jar');

const jarUrl = 'https://raw.githubusercontent.com/gradle/gradle/v7.5.1/gradle/wrapper/gradle-wrapper.jar';

async function downloadJar() {
    console.log('Downloading gradle-wrapper.jar ...');
    console.log('URL:', jarUrl);
    console.log('Dest:', destPath);
    
    if (!existsSync(destDir)) {
        mkdirSync(destDir, { recursive: true });
        console.log('Created directory:', destDir);
    }

    try {
        const response = await fetch(jarUrl);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        const buffer = Buffer.from(await response.arrayBuffer());
        console.log(`Downloaded ${buffer.length} bytes`);
        
        writeFileSync(destPath, buffer);
        console.log('Written to:', destPath);
        
        const stat = statSync(destPath);
        console.log(`File size on disk: ${stat.size} bytes`);
        
        if (stat.size > 50000) {
            console.log('SUCCESS: gradle-wrapper.jar is valid!');
        } else {
            console.warn('WARNING: File seems too small.');
        }
    } catch (err) {
        console.error('Download failed:', err.message);
        
        // Fallback: try alternative URL
        console.log('Trying alternative URL...');
        try {
            const altUrl = 'https://raw.githubusercontent.com/nicoulaj/gradle-wrapper-jar/master/gradle/wrapper/gradle-wrapper.jar';
            const resp2 = await fetch(altUrl);
            if (!resp2.ok) throw new Error(`HTTP ${resp2.status}`);
            const buf2 = Buffer.from(await resp2.arrayBuffer());
            writeFileSync(destPath, buf2);
            console.log(`Alt download: ${buf2.length} bytes - written!`);
        } catch (err2) {
            console.error('Alt also failed:', err2.message);
        }
    }
}

downloadJar();
