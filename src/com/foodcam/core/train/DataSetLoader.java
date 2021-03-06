package com.foodcam.core.train;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import com.foodcam.domain.DataSet;
import com.foodcam.domain.Histogram;
import com.foodcam.domain.ResponseMapper;
import com.foodcam.util.pRes;

/**
 * 이미지 훈련에 필요한 데이터들을 묶어 com.foodguider.domain.DataSet 형태로 리턴
 * 
 * ALL : 서버 운영에 사용되는 모든 데이터 로드
 * 
 * 아래는 인식률 테스트를 위해 사용된다 HALF_TRAIN : 이미지 DB중 반만 로드 -> 훈련 데이터로 사용 HALF_TEST :
 * HALF_TRAIN에서 로드하지 않는 나머지 반의 이미지 로드 -> 테스트 데이터로 사용
 * 
 * @author root
 *
 */
public final class DataSetLoader {
	
	public static final int ALL = -1;
//	public static final int HALF_TRAIN = -2;
//	public static final int HALF_TEST = -3;

	private DataLoader svmFeatureLoader = new FeatureLoader();
	private DataLoader histogramMatrixLoader = new HistogramMatrixLoader();

	public DataSet getTrainDataSet(int trainCount) {
		pRes.log("훈련 데이터셋 로딩을 시작합니다.");
		
		Mat trainFeatureVector = new Mat();
		ArrayList<Integer> trainLabelList = new ArrayList<>();
		ResponseMapper responseMapper = new ResponseMapper();
		
		ArrayList<Histogram> histogramList = new ArrayList<>();

		File trainDataDir = new File(pRes.TRAIN_DATA_PATH);
		if (!trainDataDir.exists()) {
			pRes.log("trainData 디렉토리를 찾을 수 없습니다");
			return null;
		}

		Random random = new Random();
		File[] dirs = trainDataDir.listFiles();
		for (int i = 0; i < dirs.length; i++) {
			File curDir = dirs[i];
			if (!curDir.isDirectory())
				continue;

			File[] files = curDir.listFiles();
			ArrayList<Integer> randIdxList = null;
			if(trainCount != ALL) {
				randIdxList = new ArrayList<>();
				while(true) {
					int randIdx = random.nextInt(files.length);
					if(randIdxList.contains(randIdx)) {
						continue;
					}
					
					randIdxList.add(randIdx);
					if(randIdxList.size() == trainCount) {
						break;
					}
				}	
			}
			
			for (int j = 0; j < (trainCount == ALL ? files.length : trainCount); j++) {
				File curFile = trainCount == ALL ? files[j] : files[randIdxList.get(j)];
				if (!curFile.isFile())
					continue;

				Mat img = Imgcodecs.imread(curFile.getAbsolutePath());
				if (img.empty())
					continue;

				Mat feature = svmFeatureLoader.load(img);
				Mat hisogramMatrix = histogramMatrixLoader.load(img);
				
				Histogram newHistogram = new Histogram();
				newHistogram.setMatrix(hisogramMatrix);
				newHistogram.setDirectoryIdx(i);
				histogramList.add(newHistogram);

				try {
					trainFeatureVector.push_back(feature);
					trainLabelList.add(i);
					responseMapper.mapResponse(i, curDir.getName());

				} catch (Exception e) {
					pRes.log("[훈련 실패] - " + curFile.getAbsolutePath());
					return null;
				}
			}
		}

		DataSet trainDataSet = new DataSet();
		trainDataSet.setFeatureVector(trainFeatureVector);
		trainDataSet.setFeatureLabelList(trainLabelList);
		trainDataSet.setResponseMapper(responseMapper);
		trainDataSet.setHistogramList(histogramList);

		pRes.log("훈련 데이터셋 로딩을 완료했습니다.");
		return trainDataSet;
	}

	/**
	 * 단일 이미지에 대한 DataSet을 로드 -> 이미지 요청 시 사용
	 * 
	 * @param receivedImg
	 * @return
	 */
	public DataSet getRequestDataSet(Mat receivedImg) {
		
		Mat feature = svmFeatureLoader.load(receivedImg);
		Mat trainFeatureVector = new Mat();

		try {
			trainFeatureVector.push_back(feature);
		} catch (Exception e) {
			pRes.log("[요청 데이터셋 로딩 실패] - ");
			return null;
		}
		
		Mat histogramMatrix = histogramMatrixLoader.load(receivedImg);
		Histogram newHistogram = new Histogram();
		newHistogram.setMatrix(histogramMatrix);
		newHistogram.setDirectoryIdx(-1);
		
		ArrayList<Histogram> histogramList = new ArrayList<>();
		histogramList.add(newHistogram);

		DataSet requestDataSet = new DataSet();
		requestDataSet.setFeatureVector(trainFeatureVector);
		requestDataSet.setHistogramList(histogramList);

		return requestDataSet;
	}
}
