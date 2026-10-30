#!/usr/bin/env node
/**
 * 运行一个真实 PR 分析任务并导出报告。
 *
 * 前置条件：后端已启动，且 pr.ai.provider 使用真实模型配置。
 * 用法：node scripts/pr-case.mjs <PR_URL>
 *
 * 可选环境变量：
 *   BASE_URL        后端地址，默认 http://127.0.0.1:18080
 *   ADMIN_USERNAME  登录用户名，默认 admin
 *   ADMIN_PASSWORD  登录密码，默认 admin123
 *   REPORT_LANGUAGE 报告语言，默认 zh-CN
 */
import {randomUUID} from 'node:crypto';
import {mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';

const prUrl = process.argv[2];
if (!prUrl) {
	console.error('Usage: node scripts/pr-case.mjs <PR_URL>');
	process.exit(1);
}

const baseUrl = (process.env.BASE_URL ?? 'http://127.0.0.1:18080').replace(/\/$/, '');
const username = process.env.ADMIN_USERNAME ?? 'admin';
const password = process.env.ADMIN_PASSWORD ?? 'admin123';
const reportLanguage = process.env.REPORT_LANGUAGE ?? 'zh-CN';
const terminalStatuses = new Set([3, 4, 5, 6]);

async function request(method, requestPath, {body, token, headers = {}} = {}) {
	const response = await fetch(baseUrl + requestPath, {
		method,
		headers: {
			'Content-Type': 'application/json',
			...(token ? {Authorization: `Bearer ${token}`} : {}),
			...headers,
		},
		body: body === undefined ? undefined : JSON.stringify(body),
	});
	const text = await response.text();
	if (!response.ok) {
		throw new Error(`${method} ${requestPath} -> HTTP ${response.status}: ${text.slice(0, 300)}`);
	}

	return {status: response.status, text, json: text ? JSON.parse(text) : undefined};
}

async function main() {
	const login = await request('POST', '/api/auth/login', {body: {username, password}});
	const token = login.json?.data?.accessToken;
	if (!token) {
		throw new Error('登录成功但没有返回 accessToken');
	}

	const created = await request('POST', '/api/tasks', {
		body: {url: prUrl, reportLanguage},
		token,
		headers: {'Idempotency-Key': randomUUID()},
	});
	const taskId = created.json?.data?.taskId;
	if (!taskId) {
		throw new Error(`创建任务失败：${created.text.slice(0, 300)}`);
	}

	console.log(`task_id=${taskId} status=${created.json.data.status}`);

	let progress;
	for (let attempt = 0; attempt < 300; attempt++) {
		const result = await request('GET', `/api/tasks/${taskId}/progress`, {token});
		progress = result.json?.data ?? {};
		console.log(`progress status=${progress.status} stage=${progress.stage} progress=${progress.progress}`);
		if (terminalStatuses.has(progress.status)) {
			break;
		}

		await new Promise(resolve => setTimeout(resolve, 2000));
	}

	if (!terminalStatuses.has(progress?.status)) {
		throw new Error('任务在 10 分钟内未进入终态');
	}

	const [detail, report, issues, files, exported] = await Promise.all([
		request('GET', `/api/tasks/${taskId}`, {token}),
		request('GET', `/api/tasks/${taskId}/report`, {token}),
		request('GET', `/api/tasks/${taskId}/issues?page=1&pageSize=100`, {token}),
		request('GET', `/api/tasks/${taskId}/files?page=1&pageSize=100`, {token}),
		request('GET', `/api/tasks/${taskId}/export`, {token}),
	]);

	const prNumber = new URL(prUrl).pathname.split('/').filter(Boolean).at(-1);
	const outDir = path.resolve('target', 'pr-cases', `pr-${prNumber}`);
	await mkdir(outDir, {recursive: true});
	const writeJson = (name, value) => writeFile(path.join(outDir, name), JSON.stringify(value, null, 2));
	await Promise.all([
		writeJson('detail.json', detail.json),
		writeJson('report.json', report.json),
		writeJson('issues.json', issues.json),
		writeJson('files.json', files.json),
		writeFile(path.join(outDir, 'report.md'), exported.text),
	]);

	const detailData = detail.json?.data ?? {};
	const issueRecords = issues.json?.data?.records ?? [];
	console.log(`task_status=${detailData.status} error_code=${detailData.errorCode ?? 'null'}`);
	console.log(`head_sha=${detailData.headSha}`);
	console.log(`issue_count=${issueRecords.length} file_count=${(files.json?.data?.records ?? []).length}`);
	for (const issue of issueRecords) {
		const location = issue.filePath ? `${issue.filePath}:${issue.startLine}-${issue.endLine}` : '报告级';
		console.log(`[${issue.issueId}] ${location} sev=${issue.severity} conf=${issue.confidence} ${issue.title}`);
	}

	console.log(`output_dir=${outDir}`);
}

main().catch(error => {
	console.error(error.message);
	process.exit(1);
});
