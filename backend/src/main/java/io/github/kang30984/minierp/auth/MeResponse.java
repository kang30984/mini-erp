package io.github.kang30984.minierp.auth;

public record MeResponse(
        Long id,
        String loginId,
        String name,
        String role,
        Long partnerId,
        String partnerName
) {

    public static MeResponse from(AppUser user) {
        return new MeResponse(user.id(), user.loginId(), user.name(), user.role(),
                user.partnerId(), user.partnerName());
    }
}
