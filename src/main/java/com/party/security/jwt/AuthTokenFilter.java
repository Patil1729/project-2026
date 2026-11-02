package com.party.security.jwt;

import com.party.security.services.UserDetailsServiceImpl;
import com.party.security.config.AuthCookieService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthEntryPointJwt entryPoint;
    private final AuthCookieService cookies;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Allow token bootstrap, signin and cookie cleanup even with an expired cookie.
        String path = request.getServletPath();
        if (path.isEmpty())
            path = request.getRequestURI().substring(request.getContextPath().length());

        return path.equals("/api/auth/csrf") || path.equals("/api/auth/signin")
                || path.equals("/api/auth/signup") || path.equals("/api/auth/signout")
                || ("GET".equals(request.getMethod()) && (path.equals("/api/public/portal/summary")
                    || path.equals("/api/public/portal/state-executive")
                    || path.equals("/api/public/portal/state-executive/positions")
                    || path.equals("/api/public/portal/organization-units")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = cookies.read(request);
        if (token != null) {
            try {
                if (token.isBlank())
                    throw new BadCredentialsException("Empty authentication cookie");

                var user = userDetailsService.loadUserById(jwtUtils.getUserIdFromJwtToken(token));

                if (!user.isEnabled())
                    throw new DisabledException("Account disabled");

                if (!jwtUtils.matchesCurrentCredentials(token, user))
                    throw new BadCredentialsException("Credentials changed; sign in again");

                var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);

            } catch (JwtException | IllegalArgumentException | AuthenticationException ex) {
                SecurityContextHolder.clearContext();
                response.addHeader("Set-Cookie", cookies.clear().toString());
                entryPoint.commence(request, response, new BadCredentialsException("Invalid or expired token"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
