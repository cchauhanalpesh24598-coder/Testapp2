import { execSync } from 'child_process';
import { existsSync, mkdirSync } from 'fs';
import { dirname } from 'path';

const jarPath = '/vercel/share/v0-project/MKNotes/gradle/wrapper/gradle-wrapper.jar';
const jarUrl = 'https://raw.githubusercontent.com/gradle/gradle/v7.5.1/gradle/wrapper/gradle-wrapper.jar';

// Ensure directory exists
const dir = dirname(jarPath);
if (!existsSync(dir)) {
    mkdirSync(dir, { recursive: true });
}

try {
    console.log('Downloading gradle-wrapper.jar from Gradle 7.5.1 release...');
    execSync(`curl -fsSL -o "${jarPath}" "${jarUrl}"`, { stdio: 'inherit' });
    console.log('Downloaded gradle-wrapper.jar successfully!');
} catch (e) {
    // Fallback: try another source
    console.log('Primary source failed, trying alternative...');
    const altUrl = 'https://services.gradle.org/distributions/gradle-7.5.1-bin.zip';
    try {
        // Download the zip, extract only the wrapper jar
        execSync(`cd /tmp && curl -fsSL -o gradle.zip "${altUrl}" && unzip -o -j gradle.zip "gradle-7.5.1/lib/gradle-wrapper-*.jar" -d /tmp/gw/ 2>/dev/null || true`, { stdio: 'inherit' });
        const jarFiles = execSync('ls /tmp/gw/*.jar 2>/dev/null || echo ""').toString().trim();
        if (jarFiles) {
            execSync(`cp "${jarFiles.split('\n')[0]}" "${jarPath}"`, { stdio: 'inherit' });
            console.log('Downloaded gradle-wrapper.jar from zip successfully!');
        } else {
            // Last resort: generate a minimal wrapper jar stub
            console.log('Creating minimal gradle-wrapper.jar...');
            execSync(`cd /tmp && mkdir -p gw_build/org/gradle/wrapper && cat > gw_build/org/gradle/wrapper/GradleWrapperMain.java << 'JAVA'
package org.gradle.wrapper;
public class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        System.err.println("Please run: gradle wrapper --gradle-version 7.5.1");
        System.exit(1);
    }
}
JAVA
javac gw_build/org/gradle/wrapper/GradleWrapperMain.java 2>/dev/null && jar cfe "${jarPath}" org.gradle.wrapper.GradleWrapperMain -C gw_build . || echo "Could not create JAR"`, { stdio: 'inherit' });
        }
    } catch (e2) {
        console.error('All download methods failed:', e2.message);
        console.log('You need to run "gradle wrapper --gradle-version 7.5.1" locally to generate the JAR.');
    }
}

// Verify
if (existsSync(jarPath)) {
    const { statSync } = await import('fs');
    const stat = statSync(jarPath);
    console.log(`gradle-wrapper.jar size: ${stat.size} bytes`);
    if (stat.size < 1000) {
        console.warn('WARNING: JAR file seems too small. You may need to regenerate it locally.');
    }
} else {
    console.error('gradle-wrapper.jar was not created!');
}
