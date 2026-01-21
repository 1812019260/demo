package com.example.demo.util;

import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * PDF 转换工具类
 * 支持将 Word、Excel、图片等格式转换为 PDF
 * 在内存中完成转换，不创建本地临时文件
 * 支持中文
 *
 * @author zxd
 * @since 2026-01-20
 */
@Slf4j
public class PdfConverter {

    /**
     * 将文件转换为 PDF 格式
     * 只转换 DOCX 和图片文件，跳过 TXT 文件
     *
     * @param fileName 文件名（用于判断文件类型）
     * @param data     文件内容（字节数组）
     * @return 转换后的 PDF 字节数组，转换失败返回 null
     */
    public static byte[] convertToPdf(String fileName, byte[] data) {
        if (fileName == null || data == null || data.length == 0) {
            log.warn("文件名或数据为空，无法转换");
            return null;
        }

        String extension = getExtension(fileName).toLowerCase();

        try {
            switch (extension) {
                case "pdf":
                    // 已经是 PDF，直接返回
                    log.info("文件已经是 PDF 格式，无需转换");
                    return data;

                case "docx":
                    // Word 2007+ 格式
                    return convertDocxToPdf(data);

                case "doc":
                    // Word 97-2003 格式（简化处理）
                    log.warn("DOC 格式转换功能暂未实现，跳过");
                    return null;

                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                case "bmp":
                    // 图片格式
                    return convertImageToPdf(data);

                case "txt":
                    // 纯文本格式 - 不转换，直接返回原数据
                    log.info("TXT 文件不转换为 PDF，保留原格式");
                    return data;

                default:
                    log.warn("不支持的文件格式：{}，跳过转换", extension);
                    return null;
            }
        } catch (Exception e) {
            log.error("转换文件 {} 失败：{}", fileName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取文件扩展名
     */
    private static String getExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return "";
    }

    /**
     * 将 Word 2007+ (.docx) 转换为 PDF
     */
    private static byte[] convertDocxToPdf(byte[] data) {
        log.info("开始转换 DOCX 文件为 PDF，原始数据大小：{} 字节", data.length);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             XWPFDocument document = new XWPFDocument(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // 使用 iText 创建 PDF 文档
            Document pdfDocument = new Document();
            PdfWriter.getInstance(pdfDocument, baos);
            pdfDocument.open();

            // 设置中文字体支持
            com.itextpdf.text.pdf.BaseFont baseFont = null;
            String[] fontPaths = {
                "C:/Windows/Fonts/simhei.ttf",      // Windows 黑体
                "C:/Windows/Fonts/simsun.ttc",      // Windows 宋体
                "C:/Windows/Fonts/msyh.ttc",        // Windows 微软雅黑
                "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",  // Linux
                "/System/Library/Fonts/PingFang.ttc",  // macOS
                "/System/Library/Fonts/STHeiti Light.ttc"  // macOS
            };

            for (String fontPath : fontPaths) {
                try {
                    baseFont = com.itextpdf.text.pdf.BaseFont.createFont(fontPath, com.itextpdf.text.pdf.BaseFont.IDENTITY_H, com.itextpdf.text.pdf.BaseFont.EMBEDDED);
                    log.info("成功加载中文字体：{}", fontPath);
                    break;
                } catch (Exception e) {
                    log.debug("字体 {} 加载失败：{}", fontPath, e.getMessage());
                }
            }

            if (baseFont == null) {
                log.warn("无法加载中文字体，尝试使用内置字体，中文可能无法显示");
                baseFont = com.itextpdf.text.pdf.BaseFont.createFont();
            }

            com.itextpdf.text.Font chineseFont = new com.itextpdf.text.Font(baseFont, 12);

            // 提取段落内容并添加到 PDF
            int paragraphCount = document.getParagraphs().size();
            log.info("DOCX 文件包含 {} 个段落", paragraphCount);

            int addedParagraphs = 0;
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    pdfDocument.add(new Paragraph(text, chineseFont));
                    addedParagraphs++;
                    log.debug("添加段落：{}", text.substring(0, Math.min(50, text.length())));
                }
            }

            pdfDocument.close();

            log.info("DOCX 转 PDF 完成，共添加 {} 个段落，输出大小：{} 字节", addedParagraphs, baos.size());

            if (addedParagraphs == 0) {
                log.warn("警告：DOCX 文件中没有提取到任何文本内容！");
            }

            return baos.toByteArray();

        } catch (Exception e) {
            log.error("DOCX 转 PDF 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将纯文本 (.txt) 转换为 PDF
     */
    private static byte[] convertTxtToPdf(byte[] data) {
        log.info("开始转换 TXT 文件为 PDF，原始数据大小：{} 字节", data.length);

        try {
            // 尝试多种编码读取文本
            String content = null;
            String[] encodings = {"UTF-8", "GBK", "GB2312", "ISO-8859-1"};

            for (String encoding : encodings) {
                try {
                    String testContent = new String(data, encoding);
                    // 检查是否包含有效的文本（非乱码）
                    if (testContent.length() > 0 && !testContent.matches(".*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F].*")) {
                        content = testContent;
                        log.info("使用 {} 编码成功读取文本，长度：{} 字符", encoding, content.length());
                        break;
                    }
                } catch (Exception e) {
                    log.debug("使用 {} 编码读取失败：{}", encoding, e.getMessage());
                }
            }

            if (content == null || content.trim().isEmpty()) {
                log.warn("无法使用常见编码读取文本内容");
                return null;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document pdfDocument = new Document();
            PdfWriter.getInstance(pdfDocument, baos);
            pdfDocument.open();

            // 设置中文字体支持
            com.itextpdf.text.pdf.BaseFont baseFont = null;
            String[] fontPaths = {
                "C:/Windows/Fonts/simhei.ttf",      // Windows 黑体
                "C:/Windows/Fonts/simsun.ttc",      // Windows 宋体
                "C:/Windows/Fonts/msyh.ttc",        // Windows 微软雅黑
                "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",  // Linux
                "/System/Library/Fonts/PingFang.ttc",  // macOS
                "/System/Library/Fonts/STHeiti Light.ttc"  // macOS
            };

            for (String fontPath : fontPaths) {
                try {
                    baseFont = com.itextpdf.text.pdf.BaseFont.createFont(fontPath, com.itextpdf.text.pdf.BaseFont.IDENTITY_H, com.itextpdf.text.pdf.BaseFont.EMBEDDED);
                    log.info("成功加载中文字体：{}", fontPath);
                    break;
                } catch (Exception e) {
                    log.debug("字体 {} 加载失败：{}", fontPath, e.getMessage());
                }
            }

            if (baseFont == null) {
                log.warn("无法加载中文字体，尝试使用内置字体，中文可能无法显示");
                baseFont = com.itextpdf.text.pdf.BaseFont.createFont();
            }

            com.itextpdf.text.Font chineseFont = new com.itextpdf.text.Font(baseFont, 12);

            // 按行添加文本
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line != null && !line.trim().isEmpty()) {
                    pdfDocument.add(new Paragraph(line, chineseFont));
                }
            }

            pdfDocument.close();

            log.info("TXT 转 PDF 完成，输出大小：{} 字节", baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("TXT 转 PDF 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将图片转换为 PDF
     */
    private static byte[] convertImageToPdf(byte[] data) {
        log.info("开始转换图片文件为 PDF");

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document pdfDocument = new Document();
            PdfWriter.getInstance(pdfDocument, baos);
            pdfDocument.open();

            // 添加图片到 PDF
            com.itextpdf.text.Image image = com.itextpdf.text.Image.getInstance(data);
            image.scaleToFit(595, 842); // A4 尺寸
            pdfDocument.add(image);

            pdfDocument.close();

            log.info("图片转 PDF 完成");
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("图片转 PDF 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 检查文件是否可以转换为 PDF
     * 只支持 DOCX 和图片文件
     *
     * @param fileName 文件名
     * @return true 表示可以转换，false 表示不能转换
     */
    public static boolean canConvertToPdf(String fileName) {
        if (fileName == null) {
            return false;
        }

        String extension = getExtension(fileName).toLowerCase();
        return extension.equals("pdf") ||
               extension.equals("doc") ||
               extension.equals("docx") ||
               extension.equals("jpg") ||
               extension.equals("jpeg") ||
               extension.equals("png") ||
               extension.equals("gif") ||
               extension.equals("bmp");
    }

    /**
     * 检查文件是否已经是 PDF 格式
     *
     * @param fileName 文件名
     * @return true 表示已经是 PDF，false 表示不是
     */
    public static boolean isPdf(String fileName) {
        return fileName != null && getExtension(fileName).toLowerCase().equals("pdf");
    }
}