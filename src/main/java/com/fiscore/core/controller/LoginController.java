package com.fiscore.core.controller;

import com.fiscore.core.models.BuildInfo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;


@SessionScope
@Controller
public class LoginController implements Serializable {

    private final BuildInfo buildInfo;
    private final AuthenticationManager authenticationManager;
    UserDetails userDetails = null;

    public LoginController(BuildInfo buildInfo, AuthenticationManager authenticationManager) {
        this.buildInfo = buildInfo;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/login")
    public String login(Model model) {
        String formattedBuildTime = buildInfo.getBuildTime() != null
                ? DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(Instant.parse(buildInfo.getBuildTime()).atZone(ZoneId.of("UTC")))
                : "No build time available";

        model.addAttribute("buildVersion", buildInfo.getVersion());
        model.addAttribute("buildTime", formattedBuildTime);
        model.addAttribute("buildNumber", buildInfo.getBuildNumber());
        return "login";
    }

    @PostMapping("/autenticar")
    public String authenticate(@RequestParam String username, @RequestParam String password, Model model, HttpSession session, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (!authentication.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("CDSF-ACCESS") || grantedAuthority.getAuthority().equals("CDSF-ADMIN"))){
                model.addAttribute("error", "Usuario no autorizado para acceder al sistema.");
                return "login";
            }

            session.setAttribute("userCompleteName", authentication.getName());
            session.setAttribute("roles", authentication.getAuthorities());
            session.setAttribute("userInfo", authentication);
            session.setAttribute("username", authentication.getName());
            return "redirect:/";
        } catch (RuntimeException e) {
            model.addAttribute("error", "El usuario o contraseña no son validos.");
            return "login";
        }
    }

    public void initAuthentication(HttpSession session, Model model) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                // Extraer el usuario autenticado y los roles
                String username;
                List<String> roles;
                Object principal = authentication.getPrincipal();
                if (principal instanceof UserDetails) {
                    userDetails = (UserDetails) principal;
                    //UserDetails userDetails = (UserDetails) principal;
                    username = userDetails.getUsername();
                    roles = userDetails.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .collect(Collectors.toList());
                } else {
                    username = principal.toString();
                    roles = authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .collect(Collectors.toList());
                }
                session.setAttribute("username", username);
                session.setAttribute("roles", roles);

                model.addAttribute("username", username);
                model.addAttribute("roles", roles);
            }
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Hubo un error en la autenticación.");
        }
    }

    @GetMapping({"/", "/principal"})
    public String principal(HttpSession session, Model model) {
        initAuthentication(session, model);
        return "index";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie("keycloak_token", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        request.getSession().invalidate();
        return "redirect:/login?logout=true";
    }
}
