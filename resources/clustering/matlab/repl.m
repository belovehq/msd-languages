
% params of the clustering
params.average = 'average';
params.distance = 'cosine';
params.clusters = 30;

% read data from files
Words = readtable('words.txt', 'Encoding', 'UTF-8');
Words.Properties.VariableNames = {'WordId', 'Word'};
M = dlmread('cowords.txt');
Normed = M ./ sum (M, 2);


% calculate hierarchical linkage
L = linkage(Normed, params.average, params.distance);

% matrix of words allocated to clusters, with cluster count 
% varying from 2 to param.clusters
Clustered = zeros(size(Words,1), params.clusters-1);
for n = 2:params.clusters
    [~, Clustered(:,n-1)] = dendrogram(L, n);
end

% output
dlmwrite('clustered.txt', Clustered')

C = clusterwords(M,10,'average', 'cosine');
M(1,2)



 
 