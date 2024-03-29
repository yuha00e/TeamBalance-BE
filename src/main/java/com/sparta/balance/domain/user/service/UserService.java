package com.sparta.balance.domain.user.service;

import com.sparta.balance.domain.user.dto.LoginRequestDto;
import com.sparta.balance.domain.user.dto.SignupRequestDto;
import com.sparta.balance.domain.user.dto.UserResponseDto;
import com.sparta.balance.domain.user.entity.User;
import com.sparta.balance.domain.user.entity.UserRoleEnum;
import com.sparta.balance.domain.user.repository.UserRepository;
import com.sparta.balance.global.handler.exception.CustomApiException;
import com.sparta.balance.global.jwt.JwtUtil;
import com.sparta.balance.global.service.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j(topic = "회원가입, 로그인 서비스 로직")
@Service
public class UserService {
    /*
    * 유저 회원가입 로그인 서비스 로직
    * userRepository : DB와 연결
    * JwtUtil : JwtToken 작업
    * passwordEncoder : 비밀번호 암호화
    * 필드 : 생성자 주입 사용
    * */
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    public UserService(UserRepository userRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    /*
    * 비밀번호 검증 패턴
    * 비밀번호 조건 최소 8 ~ 최대 15자리
    * 영문 대소문자, 숫자, 특수문자 !@#$%^&*()_~만 허용
    * passwordEncoder 패턴화를 통해 성능 최적화*/
    private static final String PASSWORD_PATTERN = "^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_~]).{8,15}$";
    private static final Pattern pattern = Pattern.compile(PASSWORD_PATTERN);

    /*
    * 회원가입 로직
    * 비밀번호 검증, 암호화 처리
    * email 중복 여부 확인*/
    public void signupUser(SignupRequestDto requestDto) {
        /*비밀번호 검증*/
        String password = requestDto.getPassword();
        if (!pattern.matcher(password).matches()) {
            throw new CustomApiException("비밀번호는 최소 8자리에서 최대 15자리이며, " +
                    "영어 대소문자(a~zA~Z), 숫자, 특수문자 !@#$%^&*()_~만 사용 가능합니다.");
        }

        /*비밀번호 암호화 처리*/
        password = passwordEncoder.encode(password);

        /*email 중복 검사*/
        String email = requestDto.getEmail();
        Optional<User> checkEmail = userRepository.findByEmail(email);
        if (checkEmail.isPresent()) {
            throw new CustomApiException("중복된 Email 입니다.");
        }

        /*유저 권한 부여
        * 관리자 권한 추가 시 권한 검증 메서드 추가 필요*/
        UserRoleEnum role = UserRoleEnum.USER;

        /*DB에 유저 정보 저장*/
        User user = new User(requestDto.getEmail(), password, requestDto.getUsername(), role);

        userRepository.save(user);
    }

    /*로그인 로직
    * 입력된 이메일과 비밀번호 일치 여부 검사 후 유저 정보로 JWT 토큰 생성 후 유저 이름과 함께 반환*/
    public UserResponseDto loginUser(LoginRequestDto requestDto) {
        String email = requestDto.getEmail();
        String password = requestDto.getPassword();

        /*이메일 확인*/
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new CustomApiException("등록된 사용자가 없습니다."));


        /*비밀번호 확인*/
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CustomApiException("비밀번호가 일치하지 않습니다.");
        }

        /*JWT 토큰 발급*/
        String accessToken = jwtUtil.createAccessToken(user.getEmail(), user.getRole());
        String refreshTokenString = jwtUtil.createRefreshToken(user.getEmail(), user.getRole());
        refreshTokenService.createAndSaveRefreshToken(user.getEmail(), refreshTokenString);

        return new UserResponseDto(user.getUsername(), accessToken, refreshTokenString);
    }

    /*로그아웃 로직*/
    public void logoutUser(String refreshTokenString) {
        /*리프레시 토큰 삭제*/
        refreshTokenService.deleteRefreshToken(refreshTokenString);
    }
}
