/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.eli.myapplication;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.graphics.PointF;
import android.opengl.GLES20;
import android.opengl.Matrix;

/**
 * A two-dimensional square for use as a drawn object in OpenGL ES 2.0.
 */
public class Interactable {

    private final String vertexShaderCode =
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "void main() {" +
                    // The matrix must be included as a modifier of gl_Position.
                    // Note that the uMVPMatrix factor *must be first* in order
                    // for the matrix multiplication product to be correct.
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}";

    private final FloatBuffer vertexBuffer;
    private final ShortBuffer drawListBuffer;
    private final int mProgram;
    private int mPositionHandle;
    private int mColorHandle;
    private int mMVPMatrixHandle;
    public float[] mModelMatrix = new float[16];

    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 3;
    static float mBorderCoords[] = {
            -0.1f,  0.1f, 0.0f,   // top left
            -0.1f, -0.1f, 0.0f,   // bottom left
            0.1f, -0.1f, 0.0f,   // bottom right
            0.1f,  0.1f, 0.0f }; // top right

    //All for AABB
    protected float mMinXCoord;
    protected float mMaxXCoord;
    protected float mMinYCoord;
    protected float mMaxYCoord;

    private final short drawOrder[] = { 0, 1, 2, 0, 2, 3 }; // order to draw vertices

    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    float color[] = { 0.6f, 0.75f, 0.6f, 0.5f };

    public float colorConstant = 0.01f;

    public float[] colorDirections = {colorConstant, colorConstant, colorConstant, colorConstant};

    //use one of the static ints declared in GameState.OBSTACLE_
    private int mType;
    private PointF[] m2dCoordArray;

    /**
     * Sets up the drawing object data for use in an OpenGL ES context.
     */
    public Interactable(float[] borderCoords, int type) {
        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (# of coordinate values * 4 bytes per float)
                borderCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(borderCoords);
        vertexBuffer.position(0);

        // initialize byte buffer for the draw list
        ByteBuffer dlb = ByteBuffer.allocateDirect(
                // (# of coordinate values * 2 bytes per short)
                drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        // prepare shaders and OpenGL program
        int vertexShader = MyGLRenderer.loadShader(
                GLES20.GL_VERTEX_SHADER,
                vertexShaderCode);
        int fragmentShader = MyGLRenderer.loadShader(
                GLES20.GL_FRAGMENT_SHADER,
                fragmentShaderCode);

        mProgram = GLES20.glCreateProgram();             // create empty OpenGL Program
        GLES20.glAttachShader(mProgram, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mProgram);                  // create OpenGL program executables

        //shouldn't need this once ball is moved to Ball class
        Matrix.setIdentityM(mModelMatrix, 0);

        mType = type;
        mBorderCoords = borderCoords;

        set2dCoordArray();
        setupAABB();

    }

    private void setupAABB(){
        float currentXMax = 0.0f;
        float currentYMax = 0.0f;
        float currentXMin = GameState.FULL_HEIGHT;
        float currentYMin = GameState.FULL_HEIGHT;

        //get AABB for X & Y coordinates
        for(int i=0;i < m2dCoordArray.length; i++){

            //for x
            if (m2dCoordArray[i].x > currentXMax)
                currentXMax = m2dCoordArray[i].x;
            if (m2dCoordArray[i].x < currentXMin)
                currentXMin = m2dCoordArray[i].x;

            //for y
            if (m2dCoordArray[i].y > currentYMax)
                currentYMax = m2dCoordArray[i].y;
            if (m2dCoordArray[i].y < currentYMin)
                currentYMin = m2dCoordArray[i].y;
        }

        mMinXCoord = currentXMin;
        mMaxXCoord = currentXMax;

        mMinYCoord = currentYMin;
        mMaxYCoord = currentYMax;

    }

    public void updateAABB(float xChange, float yChange){
        mMinXCoord = mMinXCoord + xChange;
        mMaxXCoord = mMaxXCoord + xChange;
        mMinYCoord = mMinYCoord + yChange;
        mMaxYCoord = mMaxYCoord + yChange;
    }

    /**
     * Encapsulates the OpenGL ES instructions for drawing this shape.
     *
     * @param mvpMatrix - The Model View Project matrix in which to draw
     * this shape.
     */
    public void draw(float[] mvpMatrix) {

        float temp[] = new float[16];
        // Add program to OpenGL environment
        GLES20.glUseProgram(mProgram);

        // get handle to vertex shader's vPosition member
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Prepare the triangle coordinate data
        GLES20.glVertexAttribPointer(
                mPositionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, vertexBuffer);

        // get handle to fragment shader's vColor member
        mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");

        // Set color for drawing the triangle
        GLES20.glUniform4fv(mColorHandle, 1, color, 0);


        // get handle to shape's transformation matrix
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        MyGLRenderer.checkGlError("glGetUniformLocation");

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
        MyGLRenderer.checkGlError("glUniformMatrix4fv");

        // Draw the square
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES, drawOrder.length,
                GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);


    }

    public void setCoords(float[] newCoords){
        mBorderCoords = newCoords;
    }

    public PointF[] get2dCoordArray(){

        return m2dCoordArray;
    }

    public void set2dCoordArray(){
        int numberOfCoords = mBorderCoords.length / 3;
        PointF[] coords = new PointF[numberOfCoords];

        for (int i = 0; i < numberOfCoords; i++){
            PointF currentCoord = new PointF(mBorderCoords[i*3], mBorderCoords[(i*3) + 1]);
            coords[i] = currentCoord;
        }

        m2dCoordArray = coords;
    }

    /*public void translateSquare(){
        float[] oldCoords = getCoords();
        float[] newCoords = new float[16];
        float[] mModelMatrix = new float[16];
        float[] mModelMatrixTrans = new float[16];

        Matrix.setIdentityM(mModelMatrix,0);
        Matrix.translateM(mModelMatrixTrans, 0, mModelMatrix, 0, 0.1f, 0.1f, 0);
        Matrix.multiplyMM(newCoords,0,mModelMatrixTrans,0,oldCoords,0);

        setCoords(newCoords);
    }*/

    public void setColor(float[] vertexColors){
        color = vertexColors;

        //System.out.println("Change:" + change);
        //System.out.println("Vertex:" + vertex);
        //System.out.println("Value: " + color[vertex]);

    }

    public float getColor(int vertex){
        return color[vertex];
    }

    public float getMinX(){
        return mMinXCoord;
    }

    public float getMaxX(){
        return mMaxXCoord;
    }

    public float getMinY(){
        return mMinYCoord;
    }

    public float getMaxY(){
        return mMaxYCoord;
    }

    //TODO Move this to just Ball class?
    public PointF getCenter(){

        PointF center;
        float xCenter = (mMaxXCoord + mMinXCoord) / 2;
        float yCenter = (mMaxYCoord + mMinYCoord) / 2;

        center = new PointF(xCenter, yCenter);

        return center;
    }

    public int getType(){
        return mType;
    }


}