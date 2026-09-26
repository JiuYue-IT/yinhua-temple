package com.lifebranch.server.model;

import com.lifebranch.server.validation.Text;

/** POST /api/sessions/:id/receipt 请求体：用户调整后的发现与下一步，各最多 200 字。 */
public record ReceiptRequest(
        @Text(max = 200, message = "请填写一次发现（最多 200 字）。") String insight,
        @Text(max = 200, message = "请填写现实中的下一步（最多 200 字）。") String nextStep) {
}
