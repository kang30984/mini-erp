package io.github.kang30984.minierp.auth;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    Optional<AppUser> findByLoginId(String loginId);
}
