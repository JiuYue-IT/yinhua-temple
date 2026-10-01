package com.lifebranch.server.web;

import com.lifebranch.server.model.ChoiceRequest;
import com.lifebranch.server.model.CreateSessionRequest;
import com.lifebranch.server.model.ReceiptRequest;
import com.lifebranch.server.model.SessionSnapshot;
import com.lifebranch.server.session.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 会话接口（文档 04 §4.2—4.7）。成功响应直接返回 SessionSnapshot，不包 data 层。 */
@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionSnapshot> create(@Valid @RequestBody CreateSessionRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(sessions.create(req));
    }

    @GetMapping("/sessions/{id}")
    public SessionSnapshot get(@PathVariable String id) {
        return sessions.get(id);
    }

    @PostMapping("/sessions/{id}/choice")
    public SessionSnapshot choose(@PathVariable String id, @Valid @RequestBody ChoiceRequest req) {
        return sessions.choose(id, req.optionId());
    }

    @PostMapping("/sessions/{id}/sign")
    public SessionSnapshot sign(@PathVariable String id) {
        return sessions.drawSign(id);
    }

    @PostMapping("/sessions/{id}/receipt")
    public SessionSnapshot receipt(@PathVariable String id, @Valid @RequestBody ReceiptRequest req) {
        return sessions.confirmReceipt(id, req);
    }

    @PostMapping("/reset")
    public Map<String, Boolean> reset() {
        sessions.reset();
        return Map.of("ok", true);
    }
}
