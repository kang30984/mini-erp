package io.github.kang30984.minierp.common;

/**
 * 1부터 시작하는 페이지 번호와 페이지 크기. 범위를 벗어난 값은 기본값으로 보정한다.
 */
public record PageParams(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static PageParams of(Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageParams(p, s);
    }

    public int offset() {
        return (page - 1) * size;
    }
}
