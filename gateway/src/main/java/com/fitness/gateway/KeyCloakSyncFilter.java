package com.fitness.gateway;


import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class KeyCloakSyncFilter implements WebFilter {
            private final UserService userService;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String userId=exchange.getRequest().getHeaders().getFirst("X-User-ID");
        String token=exchange.getRequest().getHeaders().getFirst("Authorization");
        RegisterRequest requestRequest=getUserDetails(token);

        if(userId==null){
            userId=requestRequest.getKeyCloakId();
        }

        if(userId!=null && token!=null){
            String finalUserId = userId;
            return userService.validateUser(userId).flatMap(exist-> {
                if (!exist) {
                    if (requestRequest != null) {
                        return userService.registerUser(requestRequest).then(Mono.empty());
                    } else {
                        return Mono.empty();
                    }
                } else {
                    log.info("User already exist,skipping sync");
                    return Mono.empty();
                }
            })
                    .then(Mono.defer(()->{
                        ServerHttpRequest mutatedRequest=exchange.getRequest().mutate().header("X-User-ID",finalUserId).build();
                        return  chain.filter(exchange.mutate().request(mutatedRequest).build());
                    }));

        }

        return chain.filter(exchange);
    }

    private RegisterRequest getUserDetails(String token) {
        try{
            String tokenWithoutBearer=token.replace("Bearer","").trim();
            SignedJWT signedJWT=SignedJWT.parse(tokenWithoutBearer);
            JWTClaimsSet claim=signedJWT.getJWTClaimsSet();

            RegisterRequest request=new RegisterRequest();
            request.setEmail(claim.getStringClaim("email"));
            request.setKeyCloakId(claim.getStringClaim("sub"));
            request.setFirstName(claim.getStringClaim("given_name"));
            request.setLastName(claim.getStringClaim("family_name"));
            request.setPassword("dummy@123123");
            return  request;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
