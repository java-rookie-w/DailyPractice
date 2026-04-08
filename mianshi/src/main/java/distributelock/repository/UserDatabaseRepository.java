package org.wang.interview.distributelock.repository;

public interface UserDatabaseRepository {

    void updateUser(String userId, String userInfo);

    String getUser(String userId);

    String[] getAllUserIds();
}
