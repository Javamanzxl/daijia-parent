package com.atguigu.daijia.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * @author ：zxl
 * @Description: request工具类
 * @ClassName: RequestUtils
 * @date ：2025/01/25 10:38
 */
public class RequestUtils {
    /**
     * 获取请求体中的数据
     * @param request
     * @return
     */
    public static String readData(HttpServletRequest request){
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        try
        {
            br = request.getReader();
            String str;
            while ((str = br.readLine()) != null)
            {
                sb.append(str);
            }
            br.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (null != br)
            {
                try
                {
                    br.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }
}
