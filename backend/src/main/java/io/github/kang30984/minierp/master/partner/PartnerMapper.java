package io.github.kang30984.minierp.master.partner;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PartnerMapper {

    long count(@Param("keyword") String keyword,
               @Param("gradeCode") String gradeCode,
               @Param("status") String status);

    List<PartnerResponse> search(@Param("keyword") String keyword,
                                 @Param("gradeCode") String gradeCode,
                                 @Param("status") String status,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    Optional<PartnerResponse> findById(@Param("id") long id);

    Optional<PartnerResponse> findByCode(@Param("code") String code);

    boolean existsByCode(@Param("code") String code);

    void insert(@Param("code") String code,
                @Param("name") String name,
                @Param("gradeCode") String gradeCode,
                @Param("status") String status);

    int update(@Param("id") long id,
               @Param("name") String name,
               @Param("gradeCode") String gradeCode,
               @Param("status") String status);
}
