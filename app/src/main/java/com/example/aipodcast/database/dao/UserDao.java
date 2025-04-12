package com.example.aipodcast.database.dao;

import com.example.aipodcast.model.User;

/**
 * Interface defining database operations for User entities.
 */
public interface UserDao {
    /**
     * Create a new user in the database
     * @param user User object to create
     * @return id of the created user, or -1 if creation failed
     */
    long createUser(User user);

    /**
     * Find user by username
     * @param username Username to search for
     * @return User object if found, null otherwise
     */
    User findByUsername(String username);

    /**
     * Find user by email
     * @param email Email to search for
     * @return User object if found, null otherwise
     */
    User findByEmail(String email);

    /**
     * Authenticate user with username and password
     * @param username Username to authenticate
     * @param password Password to verify
     * @return User object if authentication successful, null otherwise
     */
    User authenticate(String username, String password);

    /**
     * Update user information
     * @param user User object with updated information
     * @return true if update successful, false otherwise
     */
    boolean updateUser(User user);

    /**
     * Delete user from database
     * @param userId ID of user to delete
     * @return true if deletion successful, false otherwise
     */
    boolean deleteUser(long userId);
}
