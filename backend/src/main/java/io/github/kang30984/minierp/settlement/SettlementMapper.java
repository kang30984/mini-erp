package io.github.kang30984.minierp.settlement;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SettlementMapper {

    Optional<SettlementPeriod> findPeriod(@Param("yyyymm") String yyyymm);

    List<SettlementSummary> findSummaries(@Param("yyyymm") String yyyymm);

    List<SettlementDetail> findDetails(@Param("yyyymm") String yyyymm, @Param("partnerId") long partnerId);

    List<SettlementRun> findRuns(@Param("yyyymm") String yyyymm, @Param("limit") int limit);

    /** PL/pgSQL 프로시저 호출 */
    void runSettlement(@Param("yyyymm") String yyyymm,
                       @Param("userId") long userId,
                       @Param("close") boolean close);
}
