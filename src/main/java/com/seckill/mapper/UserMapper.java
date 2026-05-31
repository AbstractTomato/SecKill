package com.seckill.mapper;

import com.seckill.bean.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    /** 按手机号查询用户，登录/注册时使用 */
    @Select("""
            SELECT id, phone, nickname, password, salt, register_date, last_login_date
            FROM `user`
            WHERE phone = #{phone}
            """)
    User selectByPhone(String phone);

    /** 插入新用户，自动回填自增 id */
    @Insert("""
            INSERT INTO `user` (phone, nickname, password, salt, register_date)
            VALUES (#{phone}, #{nickname}, #{password}, #{salt}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /** 更新最近登录时间 */
    @Update("""
            UPDATE `user`
            SET last_login_date = NOW()
            WHERE id = #{id}
            """)
    int updateLastLoginDate(Long id);
}
