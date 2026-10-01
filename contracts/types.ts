// 人生支线 HTTP 契约的 TypeScript 类型。字段以 docs/04 为准，由软工 B 维护；前端可直接复制或引用。
// 所有路径以 /api 开头；除 health 与 reset 外，成功响应直接是 SessionSnapshot。

// ---------- 枚举 ----------
export type SessionMode = 'live' | 'preset';
/** 业务交互流程；省略时兼容旧请求，默认 explore。前端暂时固定 direct。 */
export type ExperienceMode = 'direct' | 'explore';

export interface WishInput {
  concern: string; // 必填，1—400 码点
  background?: string | null; // 可选，≤400
  chosenPath?: string | null; // 可选，≤100
  unchosenPath?: string | null; // 可选，≤100
  priority?: string | null; // 可选，≤150
}

/** 一次模型调用生成两层内容；正殿只显示 summary，菩提果实展开 detail。 */
export interface Reading {
  summary: { title: string; verse: string; message: string }; // 分别≤20、60、100码点
  detail: {
    understanding: string; // ≤300
    possibility: string; // ≤300
    suggestion: string; // ≤300
    nextStep: string; // ≤150
    basis: string; // ≤200
  };
}
export type SessionStatus =
  | 'generating' | 'ready' | 'reflecting' | 'ending'
  | 'sign_drawing' | 'sign_ready' // 主殿抛签：POST /sign 后先 sign_drawing，约 0.7s 后轮询到 sign_ready
  | 'complete' | 'error';
export type OptionId = 'A' | 'B';

export type DeviceMode = 'serial' | 'dryrun';
export type DeviceStatus = 'online' | 'offline' | 'dryrun';
export type DeviceEvent = 'RESET' | 'DRAW' | 'STORY' | 'REFLECT' | 'SIGN' | 'SIGN_RESULT' | 'RECEIPT' | 'ERROR';
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

/** 主殿抛出的签。preview 是可能性预演不是预测；remedy 是后悔时的补救而不是替用户做决定。 */
export interface Sign {
  title: string;    // 签题 ≤ 20
  verse: string;    // 签面短偈 ≤ 120
  preview: string;  // 当前选择的前路预演 ≤ 220
  remedy: string;   // 对后悔念头的补救 ≤ 220
  counsel: string;  // 佛家规劝色彩的温和提醒 ≤ 220
  nextStep: string; // 近期可执行的一小步 ≤ 220
  basis: string;    // 签文依据说明 ≤ 220
}

export interface StoryOption {
  id: OptionId;
  label: string;
  outcome: string;
  reflection: Reflection | null; // null = 该选项不回望
  receiptDraft: ReceiptDraft;
  sign: Sign | null;             // null = 故事未带签文，抛签时后端用旧字段生成兜底签
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
  chosenPath: string | null;
  unchosenPath: string | null;
  assumptions: string[];
  insight: string;
  nextStep: string;
  experience: ExperienceMode;
  concern: string | null;
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
  input: Input | null; // direct 时 null，使用 wish
  experience: ExperienceMode;
  wish: WishInput | null;
  reading: Reading | null; // direct generating→ready 时一次填充两层
  story: Story | null;             // generating / error 时为 null
  selectedOptionId: OptionId | null;
  sign: Sign | null;               // sign_ready 之后有值（选中项的 sign 或后端兜底签）
  receipt: Receipt | null;         // complete 前为 null
  error: ApiError | null;          // 仅 status === 'error' 时有值
}

// ---------- 请求 ----------
export type CreateSessionRequest =
  | { requestId: string; mode: 'live'; experience?: 'explore'; input: Input }
  | { requestId: string; mode: 'live'; experience: 'direct'; wish: WishInput }
  | { requestId: string; mode: 'preset'; experience?: ExperienceMode; caseId: 'team-project' };

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
