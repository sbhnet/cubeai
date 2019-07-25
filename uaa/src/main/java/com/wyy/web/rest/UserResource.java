package com.wyy.web.rest;

import com.wyy.config.Constants;
import com.codahale.metrics.annotation.Timed;
import com.wyy.domain.User;
import com.wyy.repository.UserRepository;
import com.wyy.security.AuthoritiesConstants;
import com.wyy.service.MailService;
import com.wyy.service.UserService;
import com.wyy.service.dto.UserDTO;
import com.wyy.web.rest.errors.BadRequestAlertException;
import com.wyy.web.rest.errors.EmailAlreadyUsedException;
import com.wyy.web.rest.errors.LoginAlreadyUsedException;
import com.wyy.web.rest.util.HeaderUtil;
import com.wyy.web.rest.util.JwtUtil;
import com.wyy.web.rest.util.PaginationUtil;
import io.github.jhipster.web.util.ResponseUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * REST controller for managing users.
 * <p>
 * This class accesses the User entity, and needs to fetch its collection of authorities.
 * <p>
 * For a normal use-case, it would be better to have an eager relationship between User and Authority,
 * and send everything to the client side: there would be no View Model and DTO, a lot less code, and an outer-join
 * which would be good for performance.
 * <p>
 * We use a View Model and a DTO for 3 reasons:
 * <ul>
 * <li>We want to keep a lazy association between the user and the authorities, because people will
 * quite often do relationships with the user, and we don't want them to get the authorities all
 * the time for nothing (for performance reasons). This is the #1 goal: we should not impact our users'
 * application because of this use-case.</li>
 * <li> Not having an outer join causes n+1 requests to the database. This is not a real issue as
 * we have by default a second-level cache. This means on the first HTTP call we do the n+1 requests,
 * but then all authorities come from the cache, so in fact it's much better than doing an outer join
 * (which will get lots of data from the database, for each HTTP call).</li>
 * <li> As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 * <p>
 * Another option would be to have a specific JPA entity graph to handle this case.
 */
@RestController
@RequestMapping("/api")
public class UserResource {

    private final Logger log = LoggerFactory.getLogger(UserResource.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final MailService mailService;

    public UserResource(UserRepository userRepository, UserService userService, MailService mailService) {

        this.userRepository = userRepository;
        this.userService = userService;
        this.mailService = mailService;
    }

    /**
     * POST  /users  : Creates a new user.
     * <p>
     * Creates a new user if the login and email are not already used, and sends an
     * mail with an activation link.
     * The user needs to be activated on creation.
     *
     * @param userDTO the user to create
     * @return the ResponseEntity with status 201 (Created) and with body the new user, or with status 400 (Bad Request) if the login or email is already in use
     * @throws URISyntaxException if the Location URI syntax is incorrect
     * @throws BadRequestAlertException 400 (Bad Request) if the login or email is already in use
     */
    @PostMapping("/users")
    @Timed
    @Secured({AuthoritiesConstants.ADMIN})
    public ResponseEntity<User> createUser(@Valid @RequestBody UserDTO userDTO) throws URISyntaxException {
        log.debug("REST request to save User : {}", userDTO);

        if (userDTO.getId() != null) {
            throw new BadRequestAlertException("A new user cannot already have an ID", "userManagement", "idexists");
            // Lowercase the user login before comparing with database
        } else if (userRepository.findOneByLogin(userDTO.getLogin().toLowerCase()).isPresent()) {
            throw new LoginAlreadyUsedException();
        } else if (userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()).isPresent()) {
            throw new EmailAlreadyUsedException();
        } else {
            User newUser = userService.createUser(userDTO);
            // mailService.sendCreationEmail(newUser);  // huolongshe: 创建用户不发邮件通知
            return ResponseEntity.created(new URI("/api/users/" + newUser.getLogin()))
                .headers(HeaderUtil.createAlert( "A user is created with identifier " + newUser.getLogin(), newUser.getLogin()))
                .body(newUser);
        }
    }

    /**
     * PUT /users : Updates an existing User.
     *
     * @param userDTO the user to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated user
     * @throws EmailAlreadyUsedException 400 (Bad Request) if the email is already in use
     * @throws LoginAlreadyUsedException 400 (Bad Request) if the login is already in use
     */
    @PutMapping("/users")
    @Timed
    @Secured({AuthoritiesConstants.ADMIN})
    public ResponseEntity<UserDTO> updateUser(@Valid @RequestBody UserDTO userDTO) {
        log.debug("REST request to update User : {}", userDTO);
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getId().equals(userDTO.getId()))) {
            throw new EmailAlreadyUsedException();
        }
        existingUser = userRepository.findOneByLogin(userDTO.getLogin().toLowerCase());
        if (existingUser.isPresent() && (!existingUser.get().getId().equals(userDTO.getId()))) {
            throw new LoginAlreadyUsedException();
        }
        Optional<UserDTO> updatedUser = userService.updateUser(userDTO);

        return ResponseUtil.wrapOrNotFound(updatedUser,
            HeaderUtil.createAlert("A user is updated with identifier " + userDTO.getLogin(), userDTO.getLogin()));
    }

    /**
     * GET /users : get all users.
     *
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("/users")
    @Timed
    @Secured({AuthoritiesConstants.ADMIN})
    public ResponseEntity<List<UserDTO>> getAllUsers(Pageable pageable) {
        final Page<UserDTO> page = userService.getAllManagedUsers(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/users");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * @return a string list of the all of the roles
     */
    @GetMapping("/users/authorities")
    @Timed
    @Secured({AuthoritiesConstants.ADMIN})
    public List<String> getAuthorities() {
        return userService.getAuthorities();
    }

    /**
     * @return create a new authority if not exist
     */
    @PostMapping("/users/authorities/{authority}")
    @Timed
    @Secured({AuthoritiesConstants.ADMIN})
    public ResponseEntity<Void>  createAuthority(@PathVariable String authority) {
        userService.createAuthority(authority);
        return ResponseEntity.ok().build();
    }

    /**
     * @return delete a authority
     */
    @DeleteMapping("/users/authorities/{authority}")
    @Timed
    @Secured({AuthoritiesConstants.ADMIN})
    public ResponseEntity<Void>  deleteAuthority(@PathVariable String authority) {
        if (userService.deleteAuthority(authority)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /users/:login : get the "login" user.
     *
     * @param login the login of the user to find
     * @return the ResponseEntity with status 200 (OK) and with body the "login" user, or with status 404 (Not Found)
     */
    @GetMapping("/users/{login:" + Constants.LOGIN_REGEX + "}")
    @Timed
    public ResponseEntity<UserDTO> getUser(HttpServletRequest httpServletRequest,
                                           @PathVariable String login) {
        log.debug("REST request to get User : {}", login);

        String userLogin = JwtUtil.getUserLogin(httpServletRequest);
        String userRoles = JwtUtil.getUserRoles(httpServletRequest);
        if (null == userLogin || !(userLogin.equals(login) || userLogin.equals("system") || (userRoles != null && userRoles.contains("ROLE_ADMIN")))) {
            // 只能由申请者自己或者ROLE_ADMIN查询用户信息
            return ResponseEntity.status(403).build(); // 403 Forbidden
        }

        return ResponseUtil.wrapOrNotFound(
            userService.getUserWithAuthoritiesByLogin(login)
                .map(UserDTO::new));
    }

    /**
     * DELETE /users/:login : delete the "login" User.
     *
     * @param login the login of the user to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/users/{login:" + Constants.LOGIN_REGEX + "}")
    @Timed
    @Secured({AuthoritiesConstants.ADMIN})
    public ResponseEntity<Void> deleteUser(@PathVariable String login) {
        log.debug("REST request to delete User: {}", login);
        userService.deleteUser(login);
        return ResponseEntity.ok().headers(HeaderUtil.createAlert( "A user is deleted with identifier " + login, login)).build();
    }

    /**
     * huolongshe, 20181017
     * GET /users/exist/login/:login : get the "login" user's existence.
     *
     * @param login the login of the user to find
     * @return the ResponseEntity with status 200 (OK) and with body of 1 or 0
     */
    @GetMapping("/users/exist/login/{login:^[_'.@A-Za-z0-9-]*$}")
    @Timed
    public ResponseEntity<Integer> getUserExistence(@PathVariable String login) {
        log.debug("REST request to get the existence of User : {}", login);

        Optional<User> user = this.userRepository.findOneByLogin(login.toLowerCase());

        Integer result = user.isPresent() ? 1 : 0;

        return ResponseEntity.ok().body(result);
    }

    /**
     * huolongshe, 20181017
     * GET /users/exist/email/:email : get the "email" user's existence.
     *
     * @param email the email of the user to find
     * @return the ResponseEntity with status 200 (OK) and with body of 1 or 0
     */
    @GetMapping("/users/exist/email/{email:^[_'.@A-Za-z0-9-]*$}")
    @Timed
    public ResponseEntity<Integer> getEmailExistence(@PathVariable String email) {
        log.debug("REST request to get the existence of User : {}", email);

        Optional<User> user = this.userRepository.findOneByEmailIgnoreCase(email);

        Integer result = user.isPresent() ? 1 : 0;

        return ResponseEntity.ok().body(result);
    }

    /**
     * huolongshe, 20181017
     * GET /users/exist/phone/:phone : get the "phone" user's existence.
     *
     * @param phone the phone of the user to find
     * @return the ResponseEntity with status 200 (OK) and with body of 1 or 0
     */
    @GetMapping("/users/exist/phone/{phone}")
    @Timed
    public ResponseEntity<Integer> getPhoneExistence(@PathVariable String phone) {
        log.debug("REST request to get the existence of User : {}", phone);

        Optional<User> user = this.userRepository.findOneByPhone(phone);

        Integer result = user.isPresent() ? 1 : 0;

        return ResponseEntity.ok().body(result);
    }

}
