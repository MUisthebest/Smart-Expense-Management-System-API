package com.example.smartmoney.presentation.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Public web fallback for users who open the invitation email on a desktop browser.
 * No authentication required — the page simply redirects the user to open the
 * Smart Money mobile app via the deep-link URI. The actual acceptance is performed
 * by the mobile app calling POST /api/v1/groups/invites/accept with a JWT.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class InvitePageController {

    @Value("${app.mobile-scheme:smartmoney}")
    private String mobileScheme;

    @Value("${app.web-url:https://smartmoney-gray.vercel.app}")
    private String webUrl;

    @GetMapping("/api/v1/groups/invites/accept-direct")
    public String acceptDirect(@RequestParam String token,
                               @RequestParam(required = false, defaultValue = "vi") String locale) {
        String targetLocale = ("en".equalsIgnoreCase(locale)) ? "en" : "vi";
        String webInviteUrl = webUrl + "/" + targetLocale + "/invites/group?token=" + token;
        log.info("[InvitePageController] Redirecting invite token to web: {}", webInviteUrl);
        return "redirect:" + webInviteUrl;
    }

    private String openInAppPage(String deepLink) {
        return """
                <!DOCTYPE html><html lang="en"><head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <title>Open Smart Money</title>
                  <style>
                    body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f2f5;display:flex;
                         align-items:center;justify-content:center;min-height:100vh;margin:0}
                    .card{background:#fff;border-radius:16px;padding:40px 36px;max-width:420px;
                          width:100%%;text-align:center;box-shadow:0 4px 24px rgba(0,0,0,.10)}
                    h2{color:#1a1a2e;margin-bottom:12px}
                    p{color:#555;line-height:1.6;margin-bottom:24px}
                    .btn{display:inline-block;background:#6c63ff;color:#fff;padding:14px 28px;
                         border-radius:8px;text-decoration:none;font-weight:bold}
                    .icon{font-size:48px;margin-bottom:16px}
                    .note{margin-top:20px;color:#aaa;font-size:12px}
                  </style>
                  <script>
                    // Attempt to open the app automatically; fall back to showing the button
                    window.location.href = "%s";
                  </script>
                </head><body>
                  <div class="card">
                    <div class="icon">&#x1F4F1;</div>
                    <h2>Open in Smart Money</h2>
                    <p>Tap the button below to open the Smart Money app and accept your group invitation.</p>
                    <a href="%s" class="btn">Open Smart Money App</a>
                    <p class="note">If the app does not open, make sure Smart Money is installed on your device.</p>
                  </div>
                </body></html>
                """.formatted(deepLink, deepLink);
    }
}
