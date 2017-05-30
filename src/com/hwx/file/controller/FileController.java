package com.hwx.file.controller;

import com.hwx.file.pojo.BaseFile;
import com.hwx.file.pojo.FileError;
import com.hwx.file.pojo.FilePreview;
import com.hwx.file.pojo.FilePreviewConfig;
import com.hesc.trundle.common.DateUtils;
import com.hesc.trundle.message.Message;
import com.hesc.trundle.security.StringCheck;
import com.hesc.trundle.webservice.Msg;
import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGEncodeParam;
import com.sun.image.codec.jpeg.JPEGImageEncoder;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping({ "/file" })
public class FileController {
    private Logger logger = Logger.getLogger(FileController.class);

    private String FILE_UPLOAD_ENCODING = Message.getConfig("file_upload_encoding", "UTF-8");

    private String FILE_UPLOAD_IMGEXTENSION = Message.getConfig("file_upload_imgextension",
            ".jpg,.png,.jpeg,.gif,.bmp");

    private String FILE_UPLOAD_BASEPATH = Message.getConfig("file_upload_basepath", "project");

    private String FILE_UPLOAD_EXTENSION = Message.getConfig("file_upload_extension",
            ".jpg,.png,.jpeg,.gif,.bmp,.rar,.zip,.doc,.docx,.xls,.xlsx");

    @RequestMapping(value = { "/uploadFileCK" }, method = {
            org.springframework.web.bind.annotation.RequestMethod.POST })
    public String uploadFileCK(@RequestParam MultipartFile upload, HttpServletRequest request,
            HttpServletResponse response) {
        this.logger.info("upload ckeditorImg...");

        PrintWriter pout = null;
        try {
            response.setContentType("text/html; charset=" + this.FILE_UPLOAD_ENCODING);
            response.setHeader("Cache-Control", "no-cache");
            pout = response.getWriter();

            BaseFile bf = upload(request, upload);
            if (bf == null) {
                pout.println("上传失败，可能是文件格式不匹配：" + this.FILE_UPLOAD_EXTENSION);
                return null;
            }

            String backUrl = request.getParameter("backUrl");

            String callback = request.getParameter("CKEditorFuncNum");

            response.sendRedirect(backUrl + "?cknum=" + callback + "&fileUrl=" + bf.getNewPath());

            this.logger.info("complete upload file for ckeditor : " + bf.getNewPath());
            return null;
        } catch (Exception e) {
            this.logger.error(e);
            pout.println("上传错误");
            return null;
        } finally {
            if (pout != null) {
                pout.flush();
                pout.close();
            }
        }
        //throw localObject;
    }

    @ResponseBody
    @RequestMapping(value = { "/uploadFileWeb" }, method = {
            org.springframework.web.bind.annotation.RequestMethod.POST })
    public Object uploadFileWeb(HttpServletRequest request, @RequestParam MultipartFile file) {
        this.logger.info("uploadFileWeb...");

        BaseFile bf = upload(request, file);

        if (bf == null) {
            FileError fe = new FileError();
            fe.setError("上传失败，可能是文件格式不匹配：" + this.FILE_UPLOAD_EXTENSION);
            return fe;
        }

        FilePreview filePreview = new FilePreview();

        List previews = new ArrayList();
        previews.add(getInitalPreview(bf.getNewPath(), bf.getType()));
        filePreview.setInitialPreview(previews);

        List previewConfigs = new ArrayList();
        FilePreviewConfig previewConfig = new FilePreviewConfig();
        previewConfig.setCaption(bf.getOldName());
        previewConfig.setUrl(Message.getConfig("httpPath", request.getContextPath()) + "/file/deleteFileWeb");
        previewConfig.setKey(bf.getNewName());
        previewConfigs.add(previewConfig);
        filePreview.setInitialPreviewConfig(previewConfigs);

        filePreview.setBasefile(bf);

        this.logger.info("complete uploadFileWeb : " + bf.getNewPath());
        return filePreview;
    }

    @ResponseBody
    @RequestMapping(value = { "/uploadFile" }, method = { org.springframework.web.bind.annotation.RequestMethod.POST })
    public Object uploadFile(HttpServletRequest request, @RequestParam MultipartFile file) {
        this.logger.info("upload file...");

        BaseFile bf = upload(request, file);
        if (bf == null) {
            FileError fe = new FileError();
            fe.setError("上传失败，可能是文件格式不匹配：" + this.FILE_UPLOAD_EXTENSION);
            return fe;
        }

        this.logger.info("complete upload file : " + bf.getNewPath());
        return bf;
    }

    @ResponseBody
    @RequestMapping(value = { "/uploadFiles" }, method = { org.springframework.web.bind.annotation.RequestMethod.POST })
    public Object uploadFiles(HttpServletRequest request, @RequestParam("file") MultipartFile[] files) {
        this.logger.info("upload files...");

        List bfs = new ArrayList();

        if ((files != null) && (files.length > 0)) {
            for (MultipartFile file : files) {
                BaseFile bf = upload(request, file);
                if (bf == null)
                    continue;
                bfs.add(bf);
            }
        }

        this.logger.info("complete upload files : " + bfs.size());
        return bfs;
    }

    @ResponseBody
    @RequestMapping(value = { "/deleteFileWeb" }, method = {
            org.springframework.web.bind.annotation.RequestMethod.DELETE })
    public Msg deleteFileWeb(HttpServletRequest request) {
        return new Msg();
    }

    private BaseFile upload(HttpServletRequest request, MultipartFile file) {
        BaseFile bf = new BaseFile();

        String midpath = getMidpath(request);

        String datepath = getDatepath(request);

        String maxpicm = getMaxpicm(request);

        String maxpicwidth = getMaxpicwidth(request);

        String maxpicheight = getMaxpicheight(request);
        try {
            request.setCharacterEncoding(this.FILE_UPLOAD_ENCODING);

            String fileName = file.getOriginalFilename();
            String fileextension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();

            if (this.FILE_UPLOAD_EXTENSION.indexOf(fileextension) == -1) {
                FileError fe = new FileError();
                fe.setError("只允许如下后缀：" + this.FILE_UPLOAD_EXTENSION);
                return null;
            }

            String basepath = "";
            if ("project".equals(this.FILE_UPLOAD_BASEPATH))
                basepath = request.getServletContext().getRealPath("/upload");
            else {
                basepath = this.FILE_UPLOAD_BASEPATH + "/upload";
            }

            String fullPath = basepath + midpath + datepath;

            File filepath = new File(fullPath);
            if (!filepath.exists()) {
                filepath.mkdirs();
            }

            String realfilename = DateUtils.getFormatDateTime(System.currentTimeMillis(), "yyyyMMddHHmmssSS")
                    + new Random().nextInt(1000) + fileextension;

            String fullFileName = fullPath + "/" + realfilename;

            File realfile = new File(fullFileName);
            if (file.getSize() != 0L) {
                file.transferTo(realfile);
            }
            file = null;

            bf.setLengthK(realfile.length() / 1024L);
            bf.setOldName(fileName);
            bf.setExtension(fileextension);
            bf.setType("other");

            if (this.FILE_UPLOAD_IMGEXTENSION.indexOf(fileextension) > -1) {
                bf.setType("image");
                realfilename = compressImg(realfile, fullPath, realfilename, maxpicwidth, maxpicheight, maxpicm, bf);
            }
            bf.setNewName(realfilename);

            String resultpath = Message.getConfig("httpPath", request.getContextPath()) + "/upload" + midpath + datepath
                    + "/" + realfilename;
            bf.setNewPath(resultpath);

            return bf;
        } catch (Exception e) {
            this.logger.error(e);
        }
        return null;
    }

    private String getInitalPreview(String path, String type) {
        if ("image".equals(type)) {
            return "<img src='" + path + "' class='file-preview-" + type + "' />";
        }
        return "<div class='file-preview-other'><h2><a href='" + path
                + "' target='_blank'><i class='glyphicon glyphicon-file'></i></a></h2></div>";
    }

    private String compressImg(File file, String fullPath, String realfilename, String maxpicwidth, String maxpicheight,
            String maxpicm, BaseFile bf) throws IOException {
        Image img = ImageIO.read(file);
        int newWidth = 0;
        int newHeight = 0;
        BufferedImage tag = null;
        FileOutputStream out = null;
        JPEGImageEncoder encoder = null;
        if (bf != null) {
            bf.setWidth(img.getWidth(null));
            bf.setHeight(img.getHeight(null));
        }

        if ((StringUtils.isNotEmpty(maxpicwidth)) || (StringUtils.isNotEmpty(maxpicheight))) {
            try {
                double rate = 0.0D;
                double imgwidth = img.getWidth(null);
                double imgheight = img.getHeight(null);

                if ((maxpicwidth != null) && (maxpicwidth.length() > 0)) {
                    double maxw = Double.valueOf(maxpicwidth).doubleValue();
                    if (imgwidth > maxw) {
                        double _rate = imgwidth / maxw + 0.1D;
                        if (_rate > rate)
                            rate = _rate;
                    }
                }

                if ((maxpicheight != null) && (maxpicheight.length() > 0)) {
                    double maxh = Double.valueOf(maxpicheight).doubleValue();
                    if (imgheight > maxh) {
                        double _rate = imgheight / maxh + 0.1D;
                        if (_rate > rate)
                            rate = _rate;
                    }
                }

                if (rate > 0.0D) {
                    this.logger.info("compress img for width or height");
                    newWidth = (int) (imgwidth / rate);
                    newHeight = (int) (imgheight / rate);
                    tag = new BufferedImage(newWidth, newHeight, 1);

                    tag.getGraphics().drawImage(img.getScaledInstance(newWidth, newHeight, 4), 0, 0, null);

                    realfilename = "_" + realfilename;
                    out = new FileOutputStream(fullPath + "/" + realfilename);

                    encoder = JPEGCodec.createJPEGEncoder(out);
                    encoder.encode(tag);
                    out.close();
                }
                if (bf != null) {
                    file = new File(fullPath + "/" + realfilename);
                    img = ImageIO.read(file);
                    bf.setLengthK(file.length() / 1024L);
                    bf.setWidth(img.getWidth(null));
                    bf.setHeight(img.getHeight(null));
                }
            } catch (Exception ex) {
                this.logger.error(ex);
            } finally {
                if (out != null)
                    out = null;
                if (tag != null)
                    tag = null;
                if (encoder != null)
                    encoder = null;
                if (img != null)
                    img = null;
            }

        }

        if (StringUtils.isNotEmpty(maxpicm)) {
            try {
                long maxpicml = Long.valueOf(maxpicm).longValue();
                maxpicml *= 1024L;
                file = new File(fullPath + "/" + realfilename);
                long oldm = file.length();
                if (oldm > maxpicml) {
                    this.logger.info("compress img for maxlength");
                    img = ImageIO.read(file);
                    newWidth = newWidth == 0 ? img.getWidth(null) : newWidth;
                    newHeight = newHeight == 0 ? img.getHeight(null) : newHeight;
                    float quality = (float) maxpicml / (float) oldm;
                    tag = new BufferedImage(newWidth, newHeight, 1);

                    tag.getGraphics().drawImage(img.getScaledInstance(newWidth, newHeight, 4), 0, 0, null);

                    realfilename = "_" + realfilename;
                    out = new FileOutputStream(fullPath + "/" + realfilename);

                    encoder = JPEGCodec.createJPEGEncoder(out);
                    JPEGEncodeParam jep = JPEGCodec.getDefaultJPEGEncodeParam(tag);
                    jep.setQuality(quality, true);
                    encoder.encode(tag);
                    out.close();
                }
                if (bf != null)
                    bf.setLengthK(file.length() / 1024L);
            } catch (Exception ex) {
                this.logger.error(ex);
            } finally {
                if (out != null)
                    out = null;
                if (tag != null)
                    tag = null;
                if (encoder != null)
                    encoder = null;
                if (img != null)
                    img = null;
            }
        }
        file = null;

        return realfilename;
    }

    private String getMidpath(HttpServletRequest request) {
        String midpath = "/file";
        String _midpath = request.getParameter("midpath");
        if (StringUtils.isNotEmpty(_midpath)) {
            if ("null".equals(_midpath))
                midpath = "";
            else if (StringCheck.check("(/[a-z0-9]+)+", _midpath)) {
                midpath = _midpath;
            }
        }
        this.logger.info("midpath:" + _midpath + " -> " + midpath);
        return midpath;
    }

    private String getDatepath(HttpServletRequest request) {
        String datepath = DateUtils.getCurFormatDateTime("/yyyy/MM/dd");
        String _datepath = request.getParameter("datepath");
        if (StringUtils.isNotEmpty(_datepath)) {
            if ("null".equals(_datepath))
                datepath = "";
            else if (StringCheck.check("(/[yMd]+)+", _datepath)) {
                datepath = DateUtils.getCurFormatDateTime(_datepath);
            }
        }
        this.logger.info("datepath:" + _datepath + " -> " + datepath);
        return datepath;
    }

    private String getMaxpicm(HttpServletRequest request) {
        String maxpicm = "";
        String _maxpicm = request.getParameter("maxpicm");
        if ((StringUtils.isNotEmpty(_maxpicm)) && (StringCheck.check("^[1-9]\\d*$", _maxpicm))) {
            maxpicm = _maxpicm;
        }

        this.logger.info("maxpicm:" + _maxpicm + " -> " + maxpicm);
        return maxpicm;
    }

    private String getMaxpicwidth(HttpServletRequest request) {
        String maxpicwidth = "";
        String _maxpicwidth = request.getParameter("maxpicwidth");
        if ((StringUtils.isNotEmpty(_maxpicwidth)) && (StringCheck.check("^[1-9]\\d*$", _maxpicwidth))) {
            maxpicwidth = _maxpicwidth;
        }

        this.logger.info("maxpicwidth:" + _maxpicwidth + " -> " + maxpicwidth);
        return maxpicwidth;
    }

    private String getMaxpicheight(HttpServletRequest request) {
        String maxpicheight = "";
        String _maxpicheight = request.getParameter("maxpicheight");
        if ((StringUtils.isNotEmpty(_maxpicheight)) && (StringCheck.check("^[1-9]\\d*$", _maxpicheight))) {
            maxpicheight = _maxpicheight;
        }

        this.logger.info("maxpicheight:" + _maxpicheight + " -> " + maxpicheight);
        return maxpicheight;
    }
}
