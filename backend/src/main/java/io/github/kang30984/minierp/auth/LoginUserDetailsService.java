package io.github.kang30984.minierp.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class LoginUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public LoginUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        return userMapper.findByLoginId(loginId)
                .map(LoginUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("사용자 없음: " + loginId));
    }
}
