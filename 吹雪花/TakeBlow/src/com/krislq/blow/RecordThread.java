package com.krislq.blow;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * 
 * @author kris@krislq.com
 *
 */
public class RecordThread extends Thread {
	private AudioRecord audioRecord;
	private int bufferSize = 100;
	// ������Ƶ�����ʣ�44100��Ŀǰ�ı�׼������ĳЩ�豸��Ȼ֧��22050��16000��11025
	private static int SAMPLE_RATE_IN_HZ = 44100;
	private Handler handler;
	private int what;

	private boolean stop = false;

	// �����ֵ֮�� �����¼�
	private static int BLOW_BOUNDARY = 40;

	public RecordThread(Handler handler, int what) {
		super();
		bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ,
				AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT);
		// ������Ƶ��¼�Ƶ�����CHANNEL_IN_STEREOΪ˫������CHANNEL_CONFIGURATION_MONOΪ������
		// ��Ƶ���ݸ�ʽ:PCM 16λÿ����������֤�豸֧�֡�PCM 8λÿ����������һ���ܵõ��豸֧�֡�
		audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
				SAMPLE_RATE_IN_HZ, AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT, bufferSize);
		this.handler = handler;
		this.what = what;
	}
	
	public void stopRecord()
	{
		stop = true;
	}
	public boolean getRecordStatus()
	{
		return stop;
	}

	@Override
	public void run() {
		System.out.println("RUN");
		stop = false;
		try {
			audioRecord.startRecording();
			// ���ڶ�ȡ�� buffer
			byte[] buffer = new byte[bufferSize];

			int total = 0;
			int number = 0;
			while (!stop) {
				number++;
				sleep(8);
				long currenttime = System.currentTimeMillis();
				int r = audioRecord.read(buffer, 0, bufferSize) + 1;// ��ȡ��������
				int v = 0;
				for (int i = 0; i < buffer.length; i++) {
					v += Math.abs(buffer[i]);//ȡ����ֵ����Ϊ����Ϊ��
				}
				int value = Integer.valueOf(v / r);//��õ�ǰ����ֵ��ƽ��ֵ
				System.out.println("value:" + value);
				total = total + value;
				long endtime = System.currentTimeMillis();
				long time = endtime - currenttime;
				//���ʱ�����100���벢�Ҵ�������5��
				if (time >= 100 || number > 5) {
					int tmp = total / number;
					total = 0;
					number = 0;
					//�����Ĵ�С�ﵽһ����ֵ
					if (tmp > BLOW_BOUNDARY) {
						// ������Ϣ֪ͨ������ ��������
						// ���ô����handler �����淢��֪ͨ
						handler.sendEmptyMessage(what);
						number = 1;
						time = 1;
					}
				}

			}
			audioRecord.stop();
			audioRecord.release();
			bufferSize = 100;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}