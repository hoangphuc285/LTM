#include <bits/stdc++.h>
using namespace std;
int main(){
	int a,b;
	cin>>a>>b;
	int phuc[20],i=0;	
	while (b!=0){
		phuc[i]=b%10;
		b=b/10;
		i++;
	int tong=0;
	int luy_thua=1;
	for(long long j=0;j<i;j++){
		tong=tong+phuc[j]*a*luy_thua;
		luy_thua=luy_thua*10;
	}
	cout<<tong;
	return 0;
}