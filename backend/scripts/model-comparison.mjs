import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const configPath = path.join(root, 'src/main/resources/application-dev.yml');
const config = fs.readFileSync(configPath, 'utf8');
const apiKey = process.env.PR_AI_API_KEY || config.match(/api-key:\s*(\S+)/)?.[1];
const baseUrl = process.env.PR_AI_BASE_URL || config.match(/base-url:\s*(\S+)/)?.[1];
if (!apiKey || !baseUrl) {
  throw new Error('缺少 PR_AI_API_KEY/PR_AI_BASE_URL 或本地 application-dev.yml 配置');
}

const models = [
  'deepseek/deepseek-v4.1-flash',
  'deepseek/deepseek-v4-pro',
];

const cases = [
  {
    id: 'readme-spelling',
    file: 'README',
    patch: '+My Softwarw Engineering and DevOps cloning practical.\n+Changes made fir the Software Engineering and DevOps pull request practical.',
    content: 'Hello World!\nMy Softwarw Engineering and DevOps cloning practical.\nChanges made fir the Software Engineering and DevOps pull request practical.',
    expected: '拼写错误 Softwarw / fir',
  },
  {
    id: 'js-null-boundary',
    file: 'src/display.js',
    patch: '+export function displayName(user) {\n+  return user.name.trim();\n+}',
    content: 'export function displayName(user) {\n  return user.name.trim();\n}',
    expected: 'user 可能为 null/undefined，缺少空值保护',
  },
  {
    id: 'python-exception',
    file: 'src/parse.py',
    patch: '+def parse_count(value):\n+    return int(value.strip())',
    content: 'def parse_count(value):\n    return int(value.strip())',
    expected: 'value 可能为 None 或非数字，未处理异常',
  },
  {
    id: 'ts-cross-file',
    file: 'src/normalize.ts',
    patch: '+export function normalize(value: string) {\n+  return value.trim();\n+}\n+export function callNormalize() {\n+  return normalize(undefined as any);\n+}',
    content: 'export function normalize(value: string) {\n  return value.trim();\n}\nexport function callNormalize() {\n  return normalize(undefined as any);\n}',
    expected: 'undefined 传入 normalize，可能触发运行时错误',
  },
  {
    id: 'clean-doc',
    file: 'docs/usage.md',
    patch: '+## Usage\n+\n+Run the command with a project path.',
    content: '# Tool\n\n## Usage\n\nRun the command with a project path.',
    expected: '无代码缺陷；不应产生 high/critical 问题',
  },
];

function toolDefinition() {
  const issueProps = {
    title: { type: 'string' },
    category: { type: 'integer', minimum: 1, maximum: 7 },
    severity: { type: 'integer', minimum: 1, maximum: 4 },
    confidence: { type: 'integer', minimum: 0, maximum: 100 },
    filePath: { type: 'string', description: 'Empty for report-level findings' },
    startLine: { type: 'integer' },
    endLine: { type: 'integer' },
    triggerCondition: { type: 'string' },
    impact: { type: 'string' },
    evidence: { type: 'string' },
    suggestion: { type: 'string' },
  };
  return [{
    type: 'function',
    function: {
      name: 'submit_issues',
      description: 'Submit candidate review issues for this change. Call exactly once.',
      parameters: {
        type: 'object',
        properties: {
          issues: {
            type: 'array',
            items: {
              type: 'object',
              properties: issueProps,
              required: ['title', 'category', 'severity', 'confidence', 'triggerCondition', 'impact', 'suggestion'],
              additionalProperties: false,
            },
          },
        },
        required: ['issues'],
        additionalProperties: false,
      },
    },
  }];
}

function systemPrompt() {
  return [
    '你是资深代码评审助手。只依据提供的 diff 和文件内容判断，不得编造不存在的文件、调用方或测试。',
    '必须调用 submit_issues 工具，只报告有明确证据的候选问题；没有问题时提交空数组。',
    'category: 1 逻辑, 2 空值/边界, 3 异常处理, 4 安全/敏感数据, 5 API/数据兼容, 6 测试缺失, 7 其他。',
    'severity: 1 low, 2 medium, 3 high, 4 critical。报告级问题 filePath 留空且不填行号。',
    'evidence 必须引用 diff 或内容中的具体代码；建议写成核查/修复/补测试动作。',
  ].join('\n');
}

function userPrompt(item) {
  return [
    `文件：${item.file}`,
    '变更 patch：',
    item.patch,
    '文件内容：',
    item.content,
    '请输出候选问题。',
  ].join('\n');
}

function validateIssues(args) {
  if (!args || !Array.isArray(args.issues)) return { valid: false, issues: [], reason: 'issues 不是数组' };
  const issues = [];
  for (const issue of args.issues) {
    if (!issue || typeof issue.title !== 'string' || !issue.title.trim()) continue;
    const category = Number(issue.category);
    const severity = Number(issue.severity);
    const confidence = Number(issue.confidence);
    if (!Number.isInteger(category) || category < 1 || category > 7) continue;
    if (!Number.isInteger(severity) || severity < 1 || severity > 4) continue;
    if (!Number.isInteger(confidence) || confidence < 0 || confidence > 100) continue;
    if (issue.filePath && !Number.isInteger(Number(issue.startLine))) continue;
    issues.push({
      title: issue.title,
      category,
      severity,
      confidence,
      filePath: issue.filePath || null,
      startLine: issue.startLine == null ? null : Number(issue.startLine),
      endLine: issue.endLine == null ? null : Number(issue.endLine),
    });
  }
  return { valid: issues.length === args.issues.length, issues, reason: null };
}

async function runOne(model, item) {
  const started = Date.now();
  const response = await fetch(`${baseUrl.replace(/\/$/, '')}/chat/completions`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      model,
      messages: [
        { role: 'system', content: systemPrompt() },
        { role: 'user', content: userPrompt(item) },
      ],
      tools: toolDefinition(),
      tool_choice: 'auto',
      thinking: { type: 'disabled' },
      temperature: 0.1,
      max_tokens: 2048,
    }),
  });
  const text = await response.text();
  const latencyMs = Date.now() - started;
  let body;
  try { body = JSON.parse(text); } catch { body = null; }
  if (!response.ok || !body) {
    return { model, case: item.id, ok: false, httpStatus: response.status, latencyMs, error: text.slice(0, 300) };
  }
  const message = body.choices?.[0]?.message;
  const call = message?.tool_calls?.find((item) => item.function?.name === 'submit_issues');
  const usage = body.usage || {};
  if (!call) {
    return {
      model, case: item.id, ok: true, structured: false, latencyMs,
      promptTokens: usage.prompt_tokens || 0,
      completionTokens: usage.completion_tokens || 0,
      totalTokens: usage.total_tokens || 0,
      issueCount: 0,
      note: (message?.content || '').slice(0, 200),
    };
  }
  let args;
  try { args = JSON.parse(call.function.arguments); } catch { args = null; }
  const validation = validateIssues(args);
  return {
    model, case: item.id, ok: true, structured: true,
    schemaValid: validation.valid,
    latencyMs,
    promptTokens: usage.prompt_tokens || 0,
    completionTokens: usage.completion_tokens || 0,
    totalTokens: usage.total_tokens || 0,
    issueCount: validation.issues.length,
    issues: validation.issues,
    validationReason: validation.reason,
  };
}

const results = [];
for (const model of models) {
  for (const item of cases) {
    try {
      results.push(await runOne(model, item));
    } catch (error) {
      results.push({ model, case: item.id, ok: false, error: error.message });
    }
    const last = results[results.length - 1];
    console.log(JSON.stringify({
      model: last.model,
      case: last.case,
      structured: last.structured,
      schemaValid: last.schemaValid,
      issues: last.issueCount,
      latencyMs: last.latencyMs,
      tokens: last.totalTokens,
      error: last.error || last.validationReason || undefined,
    }));
  }
}

const output = path.join(root, 'target/model-comparison-results.json');
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, JSON.stringify({ cases, results }, null, 2));
console.log('results=' + output);
