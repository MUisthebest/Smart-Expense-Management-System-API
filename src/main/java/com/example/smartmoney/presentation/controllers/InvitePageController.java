package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.group.AcceptGroupInviteRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.application.usecases.group.AcceptGroupInviteUseCase;
import com.example.smartmoney.domain.exceptions.ProjectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Slf4j
@Controller
@RequiredArgsConstructor
public class InvitePageController {

    private final AcceptGroupInviteUseCase acceptGroupInviteUseCase;

    /**
     * Email invite deep-link target. Spring Security's session-based form login protects
     * this endpoint: unauthenticated requests are redirected to /login, and after
     * successful login Spring Security replays the original request (preserving the token
     * query param) before arriving here.
     */
    @GetMapping("/api/v1/groups/invites/accept-direct")
    @ResponseBody
    public String acceptDirect(@RequestParam String token, Authentication auth) {
        try {
            AcceptGroupInviteRequest req = new AcceptGroupInviteRequest();
            req.setToken(token);
            GroupDetailResponse group = acceptGroupInviteUseCase.execute(auth.getName(), req);
            return successPage(group.getName());
        } catch (ProjectException ex) {
            log.warn("[GroupInvite] accept-direct failed for token={}: {}", token, ex.getMessage());
            return errorPage(ex.getMessage());
        } catch (Exception ex) {
            log.error("[GroupInvite] accept-direct unexpected error for token={}", token, ex);
            return errorPage("Something went wrong. Please try again or contact support.");
        }
    }

    private String successPage(String groupName) {
        return """
                <!DOCTYPE html><html lang="en"><head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <title>Invitation Accepted</title>
                  <style>
                    body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;display:flex;
                         align-items:center;justify-content:center;min-height:100vh;margin:0}
                    .card{background:#fff;border-radius:16px;padding:40px 36px;max-width:420px;
                          width:100%%;text-align:center;box-shadow:0 4px 24px rgba(0,0,0,.10)}
                    h2{color:#1a1a2e;margin-bottom:12px}
                    p{color:#555;line-height:1.6;margin-bottom:24px}
                    .icon{font-size:48px;margin-bottom:16px}
                  </style>
                </head><body>
                  <div class="card">
                    <div class="icon">🎉</div>
                    <h2>You're in!</h2>
                    <p>You have successfully joined <strong>%s</strong>.<br/>
                       Open the Smart Money app to get started.</p>
                  </div>
                </body></html>
                """.formatted(groupName);
    }

    private String errorPage(String message) {
        return """
                <!DOCTYPE html><html lang="en"><head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <title>Invitation Error</title>
                  <style>
                    body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;display:flex;
                         align-items:center;justify-content:center;min-height:100vh;margin:0}
                    .card{background:#fff;border-radius:16px;padding:40px 36px;max-width:420px;
                          width:100%%;text-align:center;box-shadow:0 4px 24px rgba(0,0,0,.10)}
                    h2{color:#c0392b;margin-bottom:12px}
                    p{color:#555;line-height:1.6}
                    .icon{font-size:48px;margin-bottom:16px}
                  </style>
                </head><body>
                  <div class="card">
                    <div class="icon">⚠️</div>
                    <h2>Could not accept invitation</h2>
                    <p>%s</p>
                  </div>
                </body></html>
                """.formatted(message);
    }
}
