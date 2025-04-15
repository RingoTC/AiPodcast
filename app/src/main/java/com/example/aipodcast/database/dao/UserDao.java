package com.example.aipodcast.database.dao;
import com.example.aipodcast.model.User;
public interface UserDao {
    long createUser(User user);
    User findByUsername(String username);
    User findByEmail(String email);
    User authenticate(String username, String password);
    boolean updateUser(User user);
    boolean deleteUser(long userId);
}