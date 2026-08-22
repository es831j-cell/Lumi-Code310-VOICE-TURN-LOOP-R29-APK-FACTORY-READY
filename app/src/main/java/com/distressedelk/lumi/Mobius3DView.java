package com.distressedelk.lumi;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** Real-time welded Möbius hologram. The ribbon is generated as one continuous half-twisted surface. */
public final class Mobius3DView extends GLSurfaceView {
    public enum VisualState { PAUSED, READY, LISTENING, THINKING, SPEAKING }
    private final MobiusRenderer renderer;

    public Mobius3DView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        renderer = new MobiusRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setFocusable(false); setClickable(false);
    }
    public void setVisualState(VisualState state) {
        final VisualState next = state == null ? VisualState.READY : state;
        queueEvent(() -> renderer.state = next);
    }

    private static final class MobiusRenderer implements GLSurfaceView.Renderer {
        private static final int SEG_U=160, SEG_V=24;
        private static final float RADIUS=1.22f, HALF_WIDTH=0.34f;
        private static final int FPV=8;
        private final float[] projection=new float[16],view=new float[16],model=new float[16],mv=new float[16],mvp=new float[16],normalMatrix=new float[16];
        private FloatBuffer vertices; private ShortBuffer indices; private int indexCount,program; private long startNanos;
        private volatile VisualState state=VisualState.READY;

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig config){
            GLES20.glClearColor(0.001f,0.002f,0.008f,1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            buildMesh(); program=buildProgram(VS,FS); startNanos=System.nanoTime();
        }
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){
            GLES20.glViewport(0,0,w,h); float aspect=h==0?1f:(float)w/h;
            Matrix.perspectiveM(projection,0,27f,aspect,0.1f,30f);
            Matrix.setLookAtM(view,0,0f,-0.02f,7.45f,0f,0.26f,0f,0f,1f,0f);
        }
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT); if(program==0)return;
            float t=(System.nanoTime()-startNanos)/1_000_000_000f,rs,wa,ws,b;
            switch(state){
                case PAUSED: rs=1.8f;wa=.002f;ws=.28f;b=.58f;break;
                case LISTENING:rs=5.0f;wa=.018f;ws=1.0f;b=1.00f;break;
                case THINKING:rs=8.0f;wa=.030f;ws=1.7f;b=1.12f;break;
                case SPEAKING:rs=6.2f;wa=.042f;ws=2.5f;b=1.16f;break;
                default:rs=3.1f;wa=.008f;ws=.50f;b=.82f;break;
            }
            Matrix.setIdentityM(model,0); Matrix.translateM(model,0,0f,.31f,0f); Matrix.scaleM(model,0,.70f,.70f,.70f);
            Matrix.rotateM(model,0,-7f,1f,0f,0f); Matrix.rotateM(model,0,-8f+t*rs,0f,1f,0f); Matrix.rotateM(model,0,3f*(float)Math.sin(t*.18f),0f,0f,1f);
            Matrix.multiplyMM(mv,0,view,0,model,0); Matrix.multiplyMM(mvp,0,projection,0,mv,0); System.arraycopy(model,0,normalMatrix,0,16);
            GLES20.glUseProgram(program);
            int ap=GLES20.glGetAttribLocation(program,"aPosition"),an=GLES20.glGetAttribLocation(program,"aNormal"),au=GLES20.glGetAttribLocation(program,"aU"),av=GLES20.glGetAttribLocation(program,"aV");
            vertices.position(0);GLES20.glVertexAttribPointer(ap,3,GLES20.GL_FLOAT,false,FPV*4,vertices);GLES20.glEnableVertexAttribArray(ap);
            vertices.position(3);GLES20.glVertexAttribPointer(an,3,GLES20.GL_FLOAT,false,FPV*4,vertices);GLES20.glEnableVertexAttribArray(an);
            vertices.position(6);GLES20.glVertexAttribPointer(au,1,GLES20.GL_FLOAT,false,FPV*4,vertices);GLES20.glEnableVertexAttribArray(au);
            vertices.position(7);GLES20.glVertexAttribPointer(av,1,GLES20.GL_FLOAT,false,FPV*4,vertices);GLES20.glEnableVertexAttribArray(av);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,mvp,0); GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uModel"),1,false,model,0); GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uNormalMatrix"),1,false,normalMatrix,0);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uTime"),t);GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uWaveAmp"),wa);GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uWaveSpeed"),ws);GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uBrightness"),b);
            indices.position(0);GLES20.glDrawElements(GLES20.GL_TRIANGLES,indexCount,GLES20.GL_UNSIGNED_SHORT,indices);
            GLES20.glDisableVertexAttribArray(ap);GLES20.glDisableVertexAttribArray(an);GLES20.glDisableVertexAttribArray(au);GLES20.glDisableVertexAttribArray(av);
        }
        private void buildMesh(){
            int rows=SEG_V+1, cols=SEG_U; float[] d=new float[rows*cols*FPV]; int p=0;
            for(int i=0;i<SEG_U;i++){ double u=Math.PI*2.0*i/SEG_U; for(int j=0;j<=SEG_V;j++){
                double v=-HALF_WIDTH+(HALF_WIDTH*2.0*j)/SEG_V; float[] pos=position(u,v),du=derivativeU(u,v),dv=derivativeV(u,v);
                float nx=du[1]*dv[2]-du[2]*dv[1],ny=du[2]*dv[0]-du[0]*dv[2],nz=du[0]*dv[1]-du[1]*dv[0],len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if(len<1e-6f)len=1f;
                d[p++]=pos[0];d[p++]=pos[1];d[p++]=pos[2];d[p++]=nx/len;d[p++]=ny/len;d[p++]=nz/len;d[p++]=(float)u;d[p++]=(float)j/SEG_V;
            }}
            short[] ix=new short[SEG_U*SEG_V*6];int q=0;
            for(int i=0;i<SEG_U;i++){ boolean seam=i==SEG_U-1; int ni=(i+1)%SEG_U; for(int j=0;j<SEG_V;j++){
                int a=i*rows+j,d0=a+1,b,c; if(seam){b=ni*rows+(SEG_V-j);c=ni*rows+(SEG_V-j-1);}else{b=ni*rows+j;c=b+1;}
                ix[q++]=(short)a;ix[q++]=(short)b;ix[q++]=(short)c;ix[q++]=(short)a;ix[q++]=(short)c;ix[q++]=(short)d0;
            }}
            vertices=ByteBuffer.allocateDirect(d.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();vertices.put(d).position(0);
            indices=ByteBuffer.allocateDirect(ix.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();indices.put(ix).position(0);indexCount=ix.length;
        }
        private static float[] position(double u,double v){double c=Math.cos(u*.5),s=Math.sin(u*.5),ring=RADIUS+v*c;return new float[]{(float)(ring*Math.cos(u)),(float)(ring*Math.sin(u)),(float)(v*s)};}
        private static float[] derivativeU(double u,double v){double e=.0008;float[]a=position(u-e,v),b=position(u+e,v);return new float[]{(float)((b[0]-a[0])/(2*e)),(float)((b[1]-a[1])/(2*e)),(float)((b[2]-a[2])/(2*e))};}
        private static float[] derivativeV(double u,double v){double e=.0008;float[]a=position(u,v-e),b=position(u,v+e);return new float[]{(float)((b[0]-a[0])/(2*e)),(float)((b[1]-a[1])/(2*e)),(float)((b[2]-a[2])/(2*e))};}
        private static int buildProgram(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs);if(v==0||f==0)return 0;int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[]ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0){GLES20.glDeleteProgram(p);p=0;}GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);return p;}
        private static int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[]ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){GLES20.glDeleteShader(s);return 0;}return s;}

        private static final String VS=
            "uniform mat4 uMvp; uniform mat4 uModel; uniform mat4 uNormalMatrix; uniform float uTime; uniform float uWaveAmp; uniform float uWaveSpeed;"+
            "attribute vec3 aPosition; attribute vec3 aNormal; attribute float aU; attribute float aV; varying vec3 vNormal; varying vec3 vWorld; varying float vWave; varying float vU; varying float vV;"+
            "void main(){float wave=sin(aU*3.0-uTime*uWaveSpeed*4.0);vec3 pos=aPosition+aNormal*(wave*uWaveAmp);vec4 world=uModel*vec4(pos,1.0);vWorld=world.xyz;vNormal=normalize((uNormalMatrix*vec4(aNormal,0.0)).xyz);vWave=.5+.5*wave;vU=aU/6.2831853;vV=aV;gl_Position=uMvp*vec4(pos,1.0);}";

        private static final String FS=
            "precision mediump float; uniform float uBrightness; varying vec3 vNormal; varying vec3 vWorld; varying float vWave; varying float vU; varying float vV;"+
            "float line(float x,float w){return 1.0-step(w,abs(x));}"+
            "float hash(float n){return fract(sin(n*91.731)*43758.5453);}"+
            "void main(){vec3 N=normalize(vNormal);vec3 V=normalize(vec3(0.0,0.0,7.0)-vWorld);vec3 L=normalize(vec3(-.45,-.2,1.0));float diff=max(dot(N,L),0.0);float rim=pow(1.0-abs(dot(N,V)),2.0);float spec=pow(max(dot(N,normalize(L+V)),0.0),64.0);"+
            "vec3 violet=vec3(.48,.06,.95), blue=vec3(.02,.38,1.0), cyan=vec3(.02,.95,1.0), gold=vec3(1.0,.48,.08);float hue=.5+.5*sin(vU*6.28318+1.1);vec3 base=mix(violet,blue,hue);base=mix(base,cyan,smoothstep(.55,1.0,vV)*.34);base=mix(base,gold,smoothstep(.72,1.0,sin(vU*12.566+2.0))*.18);"+
            "float gridU=line(fract(vU*28.0)-.5,.018);float gridV=line(fract(vV*10.0)-.5,.025);float circuitry=max(gridU*.23,gridV*.18);"+
            "float sector=floor(vU*22.0);float lu=fract(vU*22.0);float lv=vV;float active=step(.42,hash(sector));float diagA=line((lv-.5)-(lu-.5)*1.45,.045);float diagB=line((lv-.5)+(lu-.5)*1.45,.045);float bar=line(lu-.50,.040)*step(.20,lv)*step(lv,.80);float cap=line(lv-.30,.035)*step(.25,lu)*step(lu,.75);float dotRune=(1.0-step(.055,length(vec2(lu-.50,lv-.50))));float selector=hash(sector+17.0);float rune=active*step(.14,lv)*step(lv,.86)*mix(max(diagA,diagB),max(bar,max(cap,dotRune)),step(.5,selector));"+
            "vec3 glow=mix(cyan,vec3(.95,.18,1.0),hash(sector+3.0));vec3 color=base*(.12+.60*diff)+base*rim*1.35+vec3(1.0)*spec*1.15;color+=base*circuitry*.55;color+=glow*rune*(1.25+.45*vWave);color*=uBrightness;float alpha=.72+rim*.20+rune*.08;gl_FragColor=vec4(color,clamp(alpha,0.0,1.0));}";
    }
}
