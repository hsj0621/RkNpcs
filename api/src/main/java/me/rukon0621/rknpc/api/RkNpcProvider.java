package me.rukon0621.rknpc.api;

import java.util.Objects;

public final class RkNpcProvider {

    /**
     * 비동기 접근에서도 등록된 API 인스턴스를 안전하게 읽기 위해 volatile을 사용한다.
     */
    private static volatile RkNpc api;

    private RkNpcProvider() {
    }

    public static RkNpc get() {
        RkNpc current = api;
        if (current == null) {
            throw new IllegalStateException("RkNpc API is not loaded.");
        }
        return current;
    }

    /**
     * 플러그인 로드 시 CORE 모듈에서 한 번만 호출한다.
     */
    public static void register(RkNpc api) {
        if (RkNpcProvider.api != null) {
            throw new IllegalStateException("RkNpc API is already registered.");
        }
        RkNpcProvider.api = Objects.requireNonNull(api, "api");
    }

    /**
     * 현재 등록된 API 인스턴스와 호출자가 넘긴 인스턴스가 같을 때만 해제한다.
     */
    public static void unregister(RkNpc api) {
        if (RkNpcProvider.api == api) {
            RkNpcProvider.api = null;
        }
    }
}
