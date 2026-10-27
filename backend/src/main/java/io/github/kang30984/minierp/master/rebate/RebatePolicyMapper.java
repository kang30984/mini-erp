package io.github.kang30984.minierp.master.rebate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RebatePolicyMapper {

    boolean existsGrade(@Param("gradeCode") String gradeCode);

    List<GradeResponse> findGrades(@Param("today") LocalDate today);

    List<RebatePolicyResponse> findPolicies(@Param("gradeCode") String gradeCode,
                                            @Param("today") LocalDate today);

    Optional<OpenPolicy> findOpenPolicyForUpdate(@Param("gradeCode") String gradeCode);

    void closePolicy(@Param("id") long id, @Param("validTo") LocalDate validTo);

    void insertPolicy(@Param("gradeCode") String gradeCode,
                      @Param("rebateRate") BigDecimal rebateRate,
                      @Param("validFrom") LocalDate validFrom);
}
