package com.gta.remuniration.service;


import com.gta.remuniration.entity.*;
import com.gta.remuniration.exception.*;
import com.gta.remuniration.log.LogArgumentsAndResult;
import com.gta.remuniration.utils.UtilisateurHelper;
import com.gta.remuniration.utils.UtilisateurHelper.*;
import com.gta.remuniration.repository.UserRepository;
import com.gta.remuniration.security.JwtTokenProvider;
import com.gta.remuniration.utils.MailType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
//import sun.security.ssl.KerberosClientKeyExchange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


@Service
public class UserService {


    private static final String LOGIN = "login";

    private static final String EMAIL = "email";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository repository;
    @Autowired
    private user_roleService user_roleService;
    @Autowired
    private SalarieService salarieService;
    @Autowired
    private MailService mailService;
    @Autowired
    private RoleService roleService;


    @LogArgumentsAndResult
    @Transactional(readOnly = true)
    public Page<User> findAll(int pageIndex, int size) {

        Pageable pageable = (Pageable) PageRequest.of(pageIndex,  size, Sort.Direction.DESC, "login");
        return repository.findAll(pageable);

    }

    @LogArgumentsAndResult
    @Transactional(readOnly = true)
    public User findByLogin(String login) {
        if (login == null) {
            throw new NullValueException(login);
        }
        User user = repository.findByLogin(login)
                .orElseThrow(() -> new NotFoundException(User.class, login, login));
        return user;
    }
    @LogArgumentsAndResult
    @Transactional(readOnly = true)
    public User  findById(Integer id) {
        if (id == null) {
            throw new NullValueException("id");
        }
        User user;
        return user  = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(User.class, id));

    }


    @LogArgumentsAndResult
    @Transactional
    public User authenticate(String login , String password ) {

        if (login== null) {
            throw new NullValueException("login");
        }
        if (password == null) {
            throw new NullValueException("password");
        }

        User userDto = findByLogin(login);

        try { authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(login,password));
            if (!userDto.isActive()) {
                throw new UserDeactivatedException();
            }
            if (userDto.getUser_Role().contains("ADMIN")) {
                throw new InsufficientRightException();
            }
            List<user_role> user_roles  ;
            user_roles = user_roleService.findbyUserId(userDto.getId());
            List<String>roles = new ArrayList<String>();
            for (user_role role : user_roles)
            {
                roles.add(role.getRole().getNomRole());
            }

            String token= jwtTokenProvider.createToken(login,roles);
            userDto.setToken(jwtTokenProvider.createToken(login, roles));

        } catch (NotFoundException e) {
            throw new NotFoundException(User.class, LOGIN, login);
        } catch (AuthenticationException e) {
            throw new BadCredentialsException();
        }
        return userDto;
    }
    /*****************************/
   /* @Transactional(readOnly = false)
    public List<SelectionDTO> getUsersMinifiedList() {
        return repository.findByRoleAndActive(USER, true)
                .stream()
                .map(user -> SelectionDTO.builder().code(user.getId()).label(user.getFirstName() + " " + user.getLastName()).build())
                .collect(Collectors.toList());
    }*/

    @LogArgumentsAndResult
    @Transactional(readOnly = false)
    public User createUser(String login , String password , String email, String role) {
        if (login == null) {
            throw new NullValueException(login);
        }
        if (password == null) {
            throw new NullValueException(password);
        }
        if (email == null) {
            throw new NullValueException(email);
        }
        if (role == null) {
            role = "User";
        }

        if (repository.findByLogin(login).isPresent()) {
            throw new CredentialAlreadyExistsException("Login");
        }

        Salarie salarie = salarieService.findbyEmail(email);
        User user = new User();
        user.setSalarie(salarie);
        user.setPassword(passwordEncoder.encode(password));
        user.setLogin(login.toLowerCase());
        user.setActive(true);
        User saveduser = repository.save(user);
        Role Role = roleService.finfbyNom(role);
        user_role user_role= user_roleService.create(Role ,saveduser);
        return saveduser;

    }

    @LogArgumentsAndResult
    @Transactional(readOnly = false)
    public User updateUser(Integer id,  String login) {
        if (id == null) {
            throw new NullValueException("id");
        }
        if (login == null) {
            throw new NullValueException("login");
        }
        User userToUpdate = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(User.class, id));

        if (!userToUpdate.getLogin().equals(login) && repository.findByLogin(login).isPresent()) {
            throw new PropertyAlreadyUsedException(LOGIN);
        }

        userToUpdate.setLogin(login.toLowerCase());

        return (repository.save(userToUpdate));
    }

    @LogArgumentsAndResult
    @Transactional(readOnly = false)
    public User addrole(Integer id,  String role) {
        if (id == null) {
            throw new NullValueException("id");
        }
        if (role == null) {
            throw new NullValueException("role");
        }
        Role Role = roleService.finfbyNom(role);
        User userToUpdate = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(User.class, id));

        if (user_roleService.finfbyroleAndUser(Role.getId(),userToUpdate.getId())!=null) {
            throw new PropertyAlreadyUsedException("Role");
        }

        user_role user_role= user_roleService.create(Role ,userToUpdate );

        return (userToUpdate);
    }
    @LogArgumentsAndResult
    @Transactional(readOnly = false)
    public Boolean removerole(Integer id,  String role) {
        if (id == null) {
            throw new NullValueException("id");
        }
        if (role == null) {
            throw new NullValueException("role");
        }
        Role Role = roleService.finfbyNom(role);
        User userToUpdate = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(User.class, id));

        if (user_roleService.finfbyroleAndUser(Role.getId(),userToUpdate.getId())==null) {
            throw new NotFoundException(user_role.class, "Role", role);
        }

        user_roleService.delet(user_roleService.finfbyroleAndUser(Role.getId(),userToUpdate.getId()).getId());
        return true;
    }


    @LogArgumentsAndResult
    @Transactional(readOnly = false)
    public User setActive(Integer id, boolean isActive) {
        if (id == null) {
            throw new NullValueException("id");
        }
        User user = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(User.class, id));

        user.setActive(isActive);
        return (repository.save(user));
    }

    @LogArgumentsAndResult
    @Transactional(readOnly = false)
    public User changePassword(String login, String currentPassword, String newPassword) {
        if (login == null) {
            throw new InsufficientRightException();
        }
        if (currentPassword == null) {
            throw new NullValueException("currentPassword");
        }
        if (newPassword == null) {
            throw new NullValueException("newPassword");
        }
        User user = repository.findByLogin(login)
                .orElseThrow(() -> new NotFoundException(User.class, login, login));
        if (passwordEncoder.matches(currentPassword, user.getPassword())) {
            user.setPassword(passwordEncoder.encode(newPassword));
            User savedUser = repository.save(user);
            return (savedUser);
        } else {
            throw new WrongPasswordException();
        }

    }

    @LogArgumentsAndResult
    @Transactional
    public ValidationEmailDTO resetPassword(Integer id, String newPassword) {
        User user = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(User.class, id));

        Long id_salarie = repository.findSalarie(id)
                .orElseThrow(() -> new NotFoundException(User.class, id));

        Salarie salarie = salarieService.finfbyid(id_salarie);
        String email= salarie.getEmail_salarie();
        System.out.println("*******************"+email+"***************************");
        boolean checkEmailSend = mailService.send(MailType.RESET_PASSWORD, email, new String[]{newPassword});
        if (!checkEmailSend) {
            throw new SendEmailException();
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        repository.save(user);
        return ValidationEmailDTO.builder().code(200).message("Veuillez vérifier votre boite email").build();
    }

    @LogArgumentsAndResult
    @Transactional

    public ValidationEmailDTO resetPassword(String email) {
        Salarie salarie = salarieService.findbyEmail(email);
        User utilisateur = repository.findBySalarieId(salarie.getId())
                .orElseThrow(() -> new EmailNotFoundException());

        String newPassword = UtilisateurHelper.generateRandomPassword();
        boolean checkEmailSend = mailService.send(MailType.RESET_PASSWORD, email, new String[]{newPassword});
        if (!checkEmailSend) {
            throw new SendEmailException();
        }
        utilisateur.setPassword(passwordEncoder.encode(newPassword));
        repository.save(utilisateur);
        return ValidationEmailDTO.builder().code(200).message("Veuillez vérifier votre boite email").build();
    }



}

