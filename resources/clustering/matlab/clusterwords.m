% Performs hierarchical clustering of matrix *M*,
% using average and distance functions *avg* and *dst*.
% Returns *C*, the matrix of elements/rows of M allocated
% to clusters, with cluster count varying from 2 to *clust*.
% Row 1: elements allocated to 2 clusters
% ...
% Row n-1: elements allocated to n clusters.

function C = clusterwords(M,clust,avg,dst)

    % norm the rows of M
    N = M ./ sum (M,2);

    % calculate hierarchical linkage
    L = linkage(N,char(avg),char(dst));

    % output allocations to clusters
    C = zeros(size(M,1), clust-1);
    for i = 2:clust
        [~, C(:,i-1)] = dendrogram(L, i);
    end

    % transpose
    C = C';
    
end
