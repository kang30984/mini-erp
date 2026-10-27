package io.github.kang30984.minierp.common;

public final class SearchText {

    private SearchText() {
    }

    public static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    /**
     * 검색어를 ILIKE 부분 일치 패턴으로 바꾼다.
     * 사용자가 입력한 %, _ 가 와일드카드로 동작하지 않도록 이스케이프한다.
     * (PostgreSQL LIKE의 기본 이스케이프 문자는 역슬래시)
     */
    public static String containsPattern(String keyword) {
        String value = blankToNull(keyword);
        if (value == null) {
            return null;
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
