// 人生支线 HTTP 契约的 TypeScript 类型。字段以 docs/04 为准，由软工 B 维护；前端可直接复制或引用。
// 所有路径以 /api 开头；除 health 与 reset 外，成功响应直接是 SessionSnapshot。

// ---------- 枚举 ----------
export type SessionMode = 'live' | 'preset';
export type SessionStatus = 'generating' | 'ready' | 'reflecting' | 'ending' | 'complete' | 'error';
export type OptionId = 'A' | 'B';

export type DeviceMode = 'serial' | 'dryrun';
export type DeviceStatus = 'online' | 'offline' | 'dryrun';
export type DeviceEvent = 'RESET' | 'DRAW' | 'STORY' | 'REFLECT' | 'RECEIPT' | 'ERROR';
/** accepted 只表示固件已接收；done 表示定时结束，不证明实物摇动成功；unknown 为无回执。 */
export type AckStatus = 'sent' | 'accepted' | 'done' | 'cancelled' | 'error' | 'unknown';

// ---------- 输入 ----------
/** 去首尾空白后非空；长度用 Array.from(text).length 计数。 */
export interface Input {
  background: string;   // ≤ 400
  chosenPath: string;   // ≤ 100，现实中已选的道路
  unchosenPath: string; // ≤ 100，未选的道路（支线从这里开始）
  priority: string;     // ≤ 150，在意的目标
}

export const INPUT_LIMITS = {
  background: 400,
  chosenPath: 100,
  unchosenPath: 100,
  priority: 150,
} as const;

export const RECEIPT_EDIT_LIMIT = 200; // insight / nextStep 用户编辑后各 ≤ 200

// ---------- 故事 ----------
export interface Reflection {
  goal: string;        // 你原本想要
  concern: string;     // 这一步可能带来
  alternative: string; // 还有一种走法
}

export interface ReceiptDraft {
  insight: string;
  nextStep: string;
}

export interface StoryOption {
  id: OptionId;
  label: string;
  outcome: string;
  reflection: Reflection | null; // null = 该选项不回望
  receiptDraft: ReceiptDraft;
}

export interface Story {
  title: string;
  question: string;
  assumptions: string[]; // 1—3 项
  opening: string;
  decision: string;
  options: [StoryOption, StoryOption]; // 恰好 A、B
}

// ---------- 收据与快照 ----------
export interface Receipt {
  sessionId: string;
  createdAt: string; // ISO 8601 UTC
  mode: SessionMode;
  chosenPath: string;
  unchosenPath: string;
  assumptions: string[];
  insight: string;
  nextStep: string;
}

/** 错误码：见 ErrorCode */
export interface ApiError {
  code: ErrorCode | string;
  message: string; // 可直接展示给用户
}

export interface SessionSnapshot {
  id: string;
  mode: SessionMode;
  status: SessionStatus;
  input: Input;
  story: Story | null;             // generating / error 时为 null
  selectedOptionId: OptionId | null;
  receipt: Receipt | null;         // complete 前为 null
  error: ApiError | null;          // 仅 status === 'error' 时有值
}

// ---------- 请求 ----------
export type CreateSessionRequest =
  | { requestId: string; mode: 'live'; input: Input }
  | { requestId: string; mode: 'preset'; caseId: 'team-project' };

export interface ChoiceRequest { optionId: OptionId }

export interface ReceiptRequest { insight: string; nextStep: string }

// ---------- 其它响应 ----------
export interface Health {
  service: 'ok';
  aiConfigured: boolean; // 只表示配置存在，不代表服务一定可用
  device: {
    mode: DeviceMode;
    status: DeviceStatus;
    lastEvent: DeviceEvent | null;
    lastAck: AckStatus | null;
  };
}

export interface ResetResponse { ok: true }

/** 非 2xx 的响应体 */
export interface ErrorResponse { error: ApiError }

/**
 * HTTP 状态 → 错误码：
 * 400 VALIDATION_ERROR, INVALID_OPTION
 * 404 SESSION_NOT_FOUND（服务重启或已重置，需重新开始）
 * 409 REQUEST_CONFLICT, SESSION_BUSY（先 reset）, REQUEST_EXPIRED, INVALID_STATE, RECEIPT_ALREADY_CONFIRMED
 * 503 AI_NOT_CONFIGURED
 * 快照内 error.code（HTTP 200）：AI_TIMEOUT, AI_UNAVAILABLE, AI_FORMAT_ERROR
 */
export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'INVALID_OPTION'
  | 'SESSION_NOT_FOUND'
  | 'REQUEST_CONFLICT'
  | 'SESSION_BUSY'
  | 'REQUEST_EXPIRED'
  | 'INVALID_STATE'
  | 'RECEIPT_ALREADY_CONFIRMED'
  | 'AI_NOT_CONFIGURED'
  | 'AI_TIMEOUT'
  | 'AI_UNAVAILABLE'
  | 'AI_FORMAT_ERROR';
