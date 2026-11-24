import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const repo = process.env.GITHUB_REPOSITORY;
if (!repo) throw new Error('GITHUB_REPOSITORY is required');

function gh(...args) {
    return execFileSync('gh', args, { encoding: 'utf8' });
}

function readManifest(apk, key) {
    return execFileSync('apkanalyzer', ['manifest', key, apk], { encoding: 'utf8' }).trim();
}

const releasesPath = `repos/${repo}/releases`;
const pages = JSON.parse(gh('api', '--paginate', '--slurp', `${releasesPath}?per_page=100`));
const latest = pages.flat()
    .filter(release => release.draft && !release.prerelease && /^v[0-9][A-Za-z0-9._-]*$/.test(release.tag_name))
    .sort((a, b) => b.id - a.id)[0];

if (!latest) throw new Error('No Vendroid release draft found');

const releasePath = `${releasesPath}/${latest.id}`;
const draft = JSON.parse(gh('api', releasePath));
const { tag_name: tag, body: changelog } = draft;
if (!draft.draft || draft.prerelease || tag !== latest.tag_name) {
    throw new Error('The selected release draft changed. Run this workflow again.');
}
if (!changelog?.trim()) throw new Error(`Add release notes to the ${tag} draft before publishing`);

const apkName = `Vendroid-${tag}.apk`;
const apkAsset = draft.assets.find(asset => asset.name === apkName && asset.state === 'uploaded');
if (!apkAsset) throw new Error(`The ${tag} draft is missing ${apkName}`);

const workDir = mkdtempSync(join(tmpdir(), 'vendroid-release-'));
try {
    console.log(`Preparing ${tag}`);
    const apk = join(workDir, apkName);
    gh('release', 'download', tag, '--repo', repo, '--pattern', apkName, '--output', apk);

    if (readManifest(apk, 'application-id') !== 'dev.vendicated.vencord') {
        throw new Error(`${apkName} is not a Vendroid release APK`);
    }
    const versionCode = Number(readManifest(apk, 'version-code'));
    const versionName = readManifest(apk, 'version-name');
    if (!Number.isInteger(versionCode) || versionCode <= 0 || versionCode > 2_147_483_647 || !versionName) {
        throw new Error(`Invalid Android version in ${apkName}`);
    }

    const manifest = join(workDir, 'latest.json');
    const contents = JSON.stringify({ tag, versionCode, versionName, changelog }, null, 2) + '\n';
    if (Buffer.byteLength(contents) > 1024 * 1024) throw new Error('latest.json exceeds the app’s size limit');
    writeFileSync(manifest, contents);
    gh('release', 'upload', tag, '--repo', repo, '--clobber', manifest);

    const current = JSON.parse(gh('api', releasePath));
    if (!current.draft || current.prerelease || current.tag_name !== tag || current.body !== changelog
        || !current.assets.some(asset => asset.id === apkAsset.id && asset.state === 'uploaded')) {
        throw new Error('The draft changed while preparing latest.json. Review it and run this workflow again.');
    }

    gh('api', '--method', 'PATCH', releasePath, '-F', 'draft=false', '-f', 'make_latest=true');
    console.log(`Published ${tag}`);
} finally {
    rmSync(workDir, { recursive: true, force: true });
}
