package com.example.functionplotter;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 自定义 View：解析函数表达式并绘制图像（纯 Java，AIDE 可直接编译）
 * 内置轻量表达式求值，不依赖任何第三方库
 */
public class FunctionPlotView extends View {

    // ========== 颜色 ==========
    private static final int BG_COLOR   = 0xFF1A1A2E;
    private static final int GRID_COLOR = 0xFF2A2A4A;
    private static final int AXIS_COLOR = 0xFF555577;
    private static final int CURVE_COLOR = 0xFFFF9800;
    private static final int TEXT_COLOR = 0xFF8888AA;
    private static final int TICK_COLOR = 0xFF444466;

    private Paint gridPaint, axisPaint, curvePaint, labelPaint, tickPaint;

    // ========== 坐标参数 ==========
    private float scaleX = 80f;
    private float scaleY = 80f;
    private float offsetX, offsetY;

    private String expression = "";
    private String errorMessage = null;

    // 预计算的曲线路径
    private Path curvePath = null;

    // 触控
    private float lastTx, lastTy;
    private float lastPinchDist = 0;

    public FunctionPlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(BG_COLOR);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(GRID_COLOR);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);

        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(AXIS_COLOR);
        axisPaint.setStrokeWidth(2f);
        axisPaint.setStyle(Paint.Style.STROKE);

        curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        curvePaint.setColor(CURVE_COLOR);
        curvePaint.setStrokeWidth(3f);
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(TEXT_COLOR);
        labelPaint.setTextSize(28f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(TICK_COLOR);
        tickPaint.setStrokeWidth(1f);
    }

    // ========== 公开 API ==========

    public void setFunction(String expr) {
        expression = expr;
        errorMessage = null;

        // 尝试在 x=1 处求值，检测表达式合法性
        try {
            evaluate(1.0);
        } catch (Exception e) {
            errorMessage = "表达式错误: " + e.getMessage();
        }

        resetView();
        precomputeCurve();
        invalidate();
    }

    public void resetView() {
        scaleX = 80f;
        scaleY = 80f;
        offsetX = getWidth() / 2f;
        offsetY = getHeight() / 2f;
    }

    // ========== 内置表达式求值器 ==========

    /**
     * 支持：+ - * / ^ sin cos tan abs sqrt log exp pi e
     */
    private double evaluate(double xVal) {
        return parseExpr(expression, xVal);
    }

    private double parseExpr(String s, double x) {
        s = s.replace(" ", "");
        return parseAddSub(s, x);
    }

    private double parseAddSub(String s, double x) {
        int level = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') level++;
            else if (c == '(') level--;
            else if (level == 0) {
                if (c == '+') return parseAddSub(s.substring(0, i), x) + parseMulDiv(s.substring(i + 1), x);
                if (c == '-') {
                    // 确保不是负号（前面是数字或 ) 才算减号）
                    if (i > 0 && (isDigit(s.charAt(i - 1)) || s.charAt(i - 1) == ')')) {
                        return parseAddSub(s.substring(0, i), x) - parseMulDiv(s.substring(i + 1), x);
                    }
                }
            }
        }
        return parseMulDiv(s, x);
    }

    private double parseMulDiv(String s, double x) {
        int level = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') level++;
            else if (c == '(') level--;
            else if (level == 0) {
                if (c == '*') return parseMulDiv(s.substring(0, i), x) * parsePower(s.substring(i + 1), x);
                if (c == '/') return parseMulDiv(s.substring(0, i), x) / parsePower(s.substring(i + 1), x);
            }
        }
        return parsePower(s, x);
    }

    private double parsePower(String s, double x) {
        int level = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') level++;
            else if (c == '(') level--;
            else if (level == 0) {
                if (c == '^') return Math.pow(parsePower(s.substring(0, i), x), parseUnary(s.substring(i + 1), x));
            }
        }
        return parseUnary(s, x);
    }

    private double parseUnary(String s, double x) {
        if (s.length() == 0) return 0;
        // 负号
        if (s.charAt(0) == '-') return -parseUnary(s.substring(1), x);
        if (s.charAt(0) == '+') return parseUnary(s.substring(1), x);
        return parseAtom(s, x);
    }

    private double parseAtom(String s, double x) {
        if (s.length() == 0) return 0;

        // 括号
        if (s.charAt(0) == '(' && s.charAt(s.length() - 1) == ')') {
            // 确认这对括号是匹配的
            int level = 0;
            boolean matched = true;
            for (int i = 0; i < s.length() - 1; i++) {
                if (s.charAt(i) == '(') level++;
                if (s.charAt(i) == ')') level--;
                if (level == 0) { matched = false; break; }
            }
            if (matched) return parseExpr(s.substring(1, s.length() - 1), x);
        }

        // 函数
        if (s.startsWith("sin(") && s.endsWith(")")) return Math.sin(parseExpr(s.substring(4, s.length() - 1), x));
        if (s.startsWith("cos(") && s.endsWith(")")) return Math.cos(parseExpr(s.substring(4, s.length() - 1), x));
        if (s.startsWith("tan(") && s.endsWith(")")) return Math.tan(parseExpr(s.substring(4, s.length() - 1), x));
        if (s.startsWith("abs(") && s.endsWith(")")) return Math.abs(parseExpr(s.substring(4, s.length() - 1), x));
        if (s.startsWith("sqrt(") && s.endsWith(")")) return Math.sqrt(parseExpr(s.substring(5, s.length() - 1), x));
        if (s.startsWith("log(") && s.endsWith(")")) return Math.log(parseExpr(s.substring(4, s.length() - 1), x));
        if (s.startsWith("exp(") && s.endsWith(")")) return Math.exp(parseExpr(s.substring(4, s.length() - 1), x));

        // 常量
        if ("pi".equals(s)) return Math.PI;
        if ("e".equals(s)) return Math.E;
        if ("x".equals(s)) return x;

        // 数字
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new RuntimeException("无法解析: " + s);
        }
    }

    private boolean isDigit(char c) {
        return (c >= '0' && c <= '9') || c == '.';
    }

    // ========== 预计算曲线 ==========

    private void precomputeCurve() {
        if (errorMessage != null || expression.isEmpty() || getWidth() == 0) {
            curvePath = null;
            return;
        }

        Path path = new Path();
        boolean first = true;
        float step = 1f / scaleX;

        float xStart = screenToWorldX(0);
        float xEnd = screenToWorldX(getWidth());

        for (float wx = xStart; wx <= xEnd; wx += step) {
            try {
                double y = evaluate(wx);
                if (Double.isNaN(y) || Double.isInfinite(y)) {
                    first = true;
                    continue;
                }
                float sx = worldToScreenX(wx);
                float sy = worldToScreenY((float) y);
                if (sy < -2000 || sy > getHeight() + 2000) {
                    first = true;
                    continue;
                }
                if (first) {
                    path.moveTo(sx, sy);
                    first = false;
                } else {
                    path.lineTo(sx, sy);
                }
            } catch (Exception e) {
                first = true;
            }
        }
        curvePath = path;
    }

    // ========== 坐标转换 ==========

    private float worldToScreenX(float wx) { return offsetX + wx * scaleX; }
    private float worldToScreenY(float wy) { return offsetY - wy * scaleY; }
    private float screenToWorldX(float sx) { return (sx - offsetX) / scaleX; }

    // ========== 绘制 ==========

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;

        drawGrid(canvas);
        drawAxes(canvas);
        drawCurve(canvas);
        drawLabel(canvas);
    }

    private void drawGrid(Canvas canvas) {
        float gridSize = 1f;
        while (gridSize * scaleX < 50) gridSize *= 2;
        while (gridSize * scaleX > 100) gridSize /= 2;

        float xStart = (float) Math.floor(screenToWorldX(0) / gridSize) * gridSize;
        for (float wx = xStart; worldToScreenX(wx) < getWidth(); wx += gridSize) {
            float sx = worldToScreenX(wx);
            canvas.drawLine(sx, 0, sx, getHeight(), gridPaint);
        }

        float yMax = screenToWorldY(getHeight());
        float yMin = screenToWorldY(0);
        float yStart = (float) Math.floor(yMax / gridSize) * gridSize;
        for (float wy = yStart; wy > yMin; wy -= gridSize) {
            float sy = worldToScreenY(wy);
            canvas.drawLine(0, sy, getWidth(), sy, gridPaint);
        }
    }

    private void drawAxes(Canvas canvas) {
        canvas.drawLine(0, offsetY, getWidth(), offsetY, axisPaint);
        canvas.drawLine(offsetX, 0, offsetX, getHeight(), axisPaint);

        float gridSize = 1f;
        while (gridSize * scaleX < 50) gridSize *= 2;

        // x 轴刻度
        float xStart = (float) Math.floor(screenToWorldX(0) / gridSize) * gridSize;
        for (float wx = xStart; worldToScreenX(wx) < getWidth(); wx += gridSize) {
            float sx = worldToScreenX(wx);
            canvas.drawLine(sx, offsetY - 8, sx, offsetY + 8, tickPaint);
            canvas.drawText(fmtTick(wx), sx, offsetY + 24, labelPaint);
        }

        // y 轴刻度
        float yMax = screenToWorldY(getHeight());
        float yStart2 = (float) Math.floor(yMax / gridSize) * gridSize;
        for (float wy = yStart2; wy > screenToWorldY(0); wy -= gridSize) {
            float sy = worldToScreenY(wy);
            canvas.drawLine(offsetX - 8, sy, offsetX + 8, sy, tickPaint);
            canvas.drawText(fmtTick(wy), offsetX - 40, sy + 10, labelPaint);
        }

        // 原点
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(AXIS_COLOR);
        p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(offsetX, offsetY, 4, p);
        canvas.drawText("O", offsetX - 20, offsetY + 30, labelPaint);
    }

    private void drawCurve(Canvas canvas) {
        if (curvePath != null) canvas.drawPath(curvePath, curvePaint);
    }

    private void drawLabel(Canvas canvas) {
        if (!expression.isEmpty() && errorMessage == null) {
            String display = "y = " + expression;
            Paint infoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            infoPaint.setColor(0xFFFF9800);
            infoPaint.setTextSize(28f);
            infoPaint.setTextAlign(Paint.Align.LEFT);
            float tw = infoPaint.measureText(display);

            Paint bg = new Paint();
            bg.setColor(0xAA1A1A2E);
            bg.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(8, 8, 24 + tw, 44, 8, 8, bg);
            canvas.drawText(display, 16, 34, infoPaint);
        }
    }

    private String fmtTick(float v) {
        if (v == 0) return "0";
        if (v == (long) v) return String.valueOf((long) v);
        if (Math.abs(v) < 0.001) return "0";
        if (Math.abs(v) >= 1000 || Math.abs(v) <= 0.01)
            return String.format("%.1e", v);
        String s = String.format("%.1f", v);
        return s.contains(".") ? s.replaceAll("0*$", "").replaceAll("\\.$", "") : s;
    }

    // ========== 触控 ==========

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getPointerCount() == 1) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTx = e.getX();
                    lastTy = e.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    offsetX += e.getX() - lastTx;
                    offsetY += e.getY() - lastTy;
                    lastTx = e.getX();
                    lastTy = e.getY();
                    precomputeCurve();
                    invalidate();
                    break;
            }
        } else if (e.getPointerCount() == 2) {
            float dx = e.getX(0) - e.getX(1);
            float dy = e.getY(0) - e.getY(1);
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (lastPinchDist > 0) {
                float factor = dist / lastPinchDist;
                float cx = (e.getX(0) + e.getX(1)) / 2f;
                float cy = (e.getY(0) + e.getY(1)) / 2f;
                float wx = screenToWorldX(cx);
                float wy = (offsetY - cy) / scaleY;
                scaleX *= factor;
                scaleY *= factor;
                offsetX = cx - wx * scaleX;
                offsetY = cy + wy * scaleY;
                precomputeCurve();
                invalidate();
            }
            lastPinchDist = dist;
        }
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw == 0 && oldh == 0) {
            offsetX = w / 2f;
            offsetY = h / 2f;
        }
        precomputeCurve();
    }
}
