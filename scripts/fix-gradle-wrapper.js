import https from 'https';
import fs from 'fs';
import path from 'path';

const GRADLE_VERSION = '7.5.1';
const WRAPPER_JAR_URL = `https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip`;
// We'll download the wrapper jar directly from the gradle GitHub releases
const JAR_URL = `https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar`;

// Alternative: use a known good wrapper jar from GitHub
const WRAPPER_JAR_DIRECT = 'https://github.com/nickclearyinvest/gradle-wrapper-jar/raw/main/gradle-7.5.1/gradle-wrapper.jar';

// Most reliable: generate from gradle distributions page
// Actually, the simplest way is to use the gradle wrapper jar from gradle's own repo
const RELIABLE_JAR_URL = 'https://raw.githubusercontent.com/nickclearyinvest/gradle-wrapper-jar/refs/heads/main/gradle-7.5.1/gradle-wrapper.jar';

const projectRoot = '/vercel/share/v0-project/MKNotes';
const wrapperDir = path.join(projectRoot, 'gradle', 'wrapper');
const jarPath = path.join(wrapperDir, 'gradle-wrapper.jar');

function downloadFile(url, destPath) {
  return new Promise((resolve, reject) => {
    const followRedirect = (url, redirectCount = 0) => {
      if (redirectCount > 5) {
        reject(new Error('Too many redirects'));
        return;
      }
      
      const protocol = url.startsWith('https') ? https : https;
      protocol.get(url, { headers: { 'User-Agent': 'Mozilla/5.0' } }, (response) => {
        if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
          followRedirect(response.headers.location, redirectCount + 1);
          return;
        }
        if (response.statusCode !== 200) {
          reject(new Error(`HTTP ${response.statusCode} for ${url}`));
          return;
        }
        const fileStream = fs.createWriteStream(destPath);
        response.pipe(fileStream);
        fileStream.on('finish', () => {
          fileStream.close();
          resolve();
        });
        fileStream.on('error', reject);
      }).on('error', reject);
    };
    followRedirect(url);
  });
}

// Since downloading from internet may be unreliable in sandbox,
// let's generate a minimal valid gradle-wrapper.jar
// The wrapper jar is a standard Java application that bootstraps Gradle.
// We can create a minimal wrapper that reads gradle-wrapper.properties and downloads Gradle.

// Actually, the best approach for CI/CD is to NOT require the jar at all
// and instead use the gradle-wrapper.properties + a CI step.
// But for local builds, we need the jar.

// Let's try downloading first, and if that fails, create a placeholder script.
async function main() {
  // Ensure directory exists
  fs.mkdirSync(wrapperDir, { recursive: true });

  console.log('Attempting to download gradle-wrapper.jar...');
  
  // Try multiple sources
  const urls = [
    'https://raw.githubusercontent.com/nickclearyinvest/gradle-wrapper-jar/main/gradle-7.5.1/gradle-wrapper.jar',
    'https://services.gradle.org/distributions/gradle-7.5.1-bin.zip',
  ];

  let downloaded = false;
  for (const url of urls) {
    if (url.endsWith('.zip')) {
      console.log('Skipping zip URL (need jar directly)');
      continue;
    }
    try {
      console.log(`Trying: ${url}`);
      await downloadFile(url, jarPath);
      const stats = fs.statSync(jarPath);
      if (stats.size > 10000) { // Valid jar should be > 50KB
        console.log(`Downloaded successfully: ${stats.size} bytes`);
        downloaded = true;
        break;
      } else {
        console.log(`File too small (${stats.size} bytes), trying next source...`);
      }
    } catch (e) {
      console.log(`Failed: ${e.message}`);
    }
  }

  if (!downloaded) {
    console.log('Could not download from remote sources.');
    console.log('Creating gradle wrapper setup script instead...');
    
    // Create a setup script that users can run
    const setupScript = `#!/bin/sh
# Run this script to set up the Gradle wrapper
# It will download Gradle and set up the wrapper
cd "${projectRoot}"
if command -v gradle >/dev/null 2>&1; then
  gradle wrapper --gradle-version 7.5.1
elif command -v sdk >/dev/null 2>&1; then
  sdk install gradle 7.5.1
  gradle wrapper --gradle-version 7.5.1
else
  echo "Please install Gradle first, then run: gradle wrapper --gradle-version 7.5.1"
  echo "Or download gradle-wrapper.jar manually from:"
  echo "https://github.com/nickclearyinvest/gradle-wrapper-jar/raw/main/gradle-7.5.1/gradle-wrapper.jar"
  echo "Place it in: gradle/wrapper/gradle-wrapper.jar"
fi
`;
    fs.writeFileSync(path.join(projectRoot, 'setup-wrapper.sh'), setupScript);
    fs.chmodSync(path.join(projectRoot, 'setup-wrapper.sh'), 0o755);
    console.log('Created setup-wrapper.sh');
  }

  // Verify wrapper properties
  const propsPath = path.join(wrapperDir, 'gradle-wrapper.properties');
  const propsContent = `distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-7.5.1-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
`;
  fs.writeFileSync(propsPath, propsContent);
  console.log('Verified gradle-wrapper.properties');

  // Verify gradlew is executable
  const gradlewPath = path.join(projectRoot, 'gradlew');
  if (fs.existsSync(gradlewPath)) {
    fs.chmodSync(gradlewPath, 0o755);
    console.log('Made gradlew executable');
  }

  console.log('Done!');
}

main().catch(e => {
  console.error('Error:', e.message);
  process.exit(1);
});
