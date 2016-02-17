# Propensity Score Matching in R
# Copyright 2013 by Ani Katchova

# install.packages("Matching")
library(Matching)
# install.packages("rbounds")
library(rbounds)
library(xlsx)
library(rgenoud)

load.libraries <- function(libraries_needed) {
	for(library_needed in libraries_needed)
		if(!library(library_needed, quietly=TRUE, logical.return=TRUE, character.only=TRUE)) {
			install.packages(library_needed)
			if(!library(library_needed, quietly=TRUE, logical.return=TRUE, character.only=TRUE))
				stop(paste("Unable to load library '", library_needed, "'.", sep=""))
		}
}

do.h.psm <- function(hdata) {
    attach(hdata)
    browser()
	# Defining variables (Tr is treatment, Y is outcome, X are independent variables)
	Tr <- cbind(received.heparin.within.2.days)
	Y <- cbind(deceased.within.28.days)
	X <- cbind(SOFA.score.day.1, age, saps1.day.1)
	var1 <- age
    var2 <- SOFA.score.day.1
	var3 <- saps1.day.1

	# Outcome for difference-in-differences model
	# Y <- cbind(REDIFF)

	# Descriptive statistics
	summary(Tr)
	print(summary(Y))
	print(summary(X))
	  
	# Propensity score model 
	hglm <- glm(Y ~ X, family=binomial(link = "probit"), data=hdata)
	print(summary(hglm))

	# Average treatment on the treated effect
	#rr1 <- Match(Y = Y, Tr = Tr, X = glm1$fitted)
	#summary(rr1)
	# rr2 <- Match(Y = Y, Tr = Tr, X = glm1$fitted, estimand = "ATT", M = 1, ties = TRUE, replace = TRUE)
	# rr3 <- Match(Y = Y, Tr = Tr, X = glm1$fitted, estimand = "ATE", M = 1, ties = TRUE, replace = TRUE)

	# Checking the balancing property
	#MatchBalance(Tr ~ X, match.out = rr1, nboots=0, data=hdata)
	#windows()
	#qqplot(var1[rr1$index.control], var1[rr1$index.treated])
	#abline(coef = c(0, 1), col = 2)

	# Genetic matching
	hgen <- GenMatch(Tr = Tr, X = X, BalanceMatrix = X, pop.size = 10)
	hmgen <- Match(Y = Y, Tr = Tr, X = X, Weight.matrix = hgen)
	MatchBalance(Tr ~ X, data = hdata, match.out = hmgen, nboots = 0)
	print(summary(hmgen))

	windows()
	qqplot(var1[hmgen$index.control], var1[hmgen$index.treated])
	abline(coef = c(0, 1), col = 2)
	qqplot(var2[hmgen$index.control], var2[hmgen$index.treated])
	abline(coef = c(0, 1), col = 2)
	qqplot(var3[hmgen$index.control], var3[hmgen$index.treated])
	abline(coef = c(0, 1), col = 2)

	# Sensitivity tests
	print(psens(hmgen, Gamma=1.7, GammaInc=.05))
	print(hlsens(hmgen, Gamma=1.7, GammaInc=.05, .1))
}

guarantee.dir <- function(dir) {
	dir.create(dir, showWarnings = FALSE)	
}

factor.as.function <- function(fu, fa) {
	fu(levels(fa)[fa])
}

data.avlb <- function(d) {
	1 - sum(is.na(d))/length(d)
}

do.bslr <- function() {
	fullmod = glm(low ~ age+lwt+racefac+smoke+ptl+ht+ui+ftv,family=binomial)
	backwards = step(fullmod)
}

missing.data <- function(d, round_n=2) {
	round(100*sum(is.na(d))/length(d), round_n)
}

table6.row <- function(v, round_n=2) {
	print(paste0("JAAM DIC: ", round(mean(v[has.jaam.dic.day.1], na.rm=TRUE), round_n), " =- ", round(sd(v[has.jaam.dic.day.1], na.rm=TRUE), round_n)))
	print(paste0("Non-DIC: ", round(mean(v[no.jaam.dic.day.1], na.rm=TRUE), round_n), " =- ", round(sd(v[no.jaam.dic.day.1], na.rm=TRUE), round_n)))
	print(paste0("P value: ", t.test(v[has.jaam.dic.day.1], v[no.jaam.dic.day.1])$p.value))
	print(paste0("Missing data: ", missing.data(v[!is.na(jaam.score.day.1)], round_n)))
}

number.of.n.in.jaam.score <- function() {
	jaam.scores <- unique(jaam.score.day.1[!is.na(jaam.score.day.1)])
	jaam.scores <- jaam.scores[order(jaam.scores)]
	for(jaam.score in jaam.scores)
		print(paste0(jaam.score, ": n=", sum(jaam.score.day.1==jaam.score, na.rm=TRUE)))
}

table7.row <- function(v, round_n=2) {
	jaam.scores <- unique(jaam.score.day.1[!is.na(jaam.score.day.1)])
	jaam.scores <- jaam.scores[order(jaam.scores)]
	ret <- character(length(jaam.scores))
	for(jaam.score in jaam.scores) {
		ret[jaam.score] <- paste0(round(mean(v[jaam.score.day.1==jaam.score], na.rm=TRUE), round_n),
			" =- ", round(sd(v[jaam.score.day.1==jaam.score], na.rm=TRUE), round_n))
	}
	ret
}
#
## Program execution begins here.
data.dir <- "Output/AMIA 2015/"
infile <- paste0(data.dir, "HEDICIM 15-03-12 AM 07-37 MIMICII.csv")

message("Codename: HEDICIM AMIA 2015")
message("By Fabricio Kury, MD, James J. Cimino, MD")
message("Project start: January 2015")

message("Loading libraries...")
load.libraries(c('ggplot2', 'plyr'))
message("...success.")

guarantee.dir(data.dir)

hdata <- read.csv(infile)
hdata <- subset(hdata, has.ICD9.for.sepsis==TRUE)

hdata$age[!is.na(hdata$age) & hdata$age > 95] <- 95
levels(hdata$gender)[3] <- "T"
hdata$gender <- factor.as.function(as.logical, hdata$gender)
hdata$pt.inr.day.1 <- factor.as.function(as.numeric, hdata$pt.inr.day.1)
#hdata$pt.inr.day.4 <- factor.as.function(as.numeric, hdata$pt.inr.day.4)
#hdata$pt.day.1 <- factor.as.function(as.numeric, hdata$pt.day.1)
hdata$platelets.day.1 <- factor.as.function(as.numeric, hdata$platelets.day.1)
#hdata$pt.day.4 <- factor.as.function(as.numeric, hdata$pt.day.4)

message("Student't t test for the value of JAAM DIC diagnosis at day one in predicting death within 28 days...")
A.group <- hdata$deceased.within.28.days[!is.na(hdata$jaam.score.day.1) & hdata$jaam.score.day.1>=4]
B.group <- hdata$deceased.within.28.days[!is.na(hdata$jaam.score.day.1) & hdata$jaam.score.day.1<4]
t.test(A.group, B.group, var.equal=TRUE)

data.avlb(hdata$jaam.score.day.1 & hdata$age)
data.avlb(hdata$jaam.score.day.1)
round(data.avlb(hdata$gender)*100,2)
round(data.avlb(hdata$gender)*100,1)
round(data.avlb(hdata$gender)*100,1)
round(data.avlb(hdata$platelets.day.1)*100,1)
round(data.avlb(hdata$platelets.day.4)*100,1)
summary(hdata$gender[hdata$jaam.score.day.1>=4])
summary(hdata$gender[hdata$jaam.score.day.1<4])
mean(hdata$platelets.day.1[hdata$jaam.score.day.1>=4], na.rm=TRUE)
sd(hdata$platelets.day.1[hdata$jaam.score.day.1>=4], na.rm=TRUE)
mean(hdata$platelets.day.1[hdata$jaam.score.day.1<4], na.rm=TRUE)
sd(hdata$platelets.day.1[hdata$jaam.score.day.1<4], na.rm=TRUE)
mean(hdata$pt.day.1[hdata$jaam.score.day.1<4], na.rm=TRUE)
sd(hdata$pt.day.1[hdata$jaam.score.day.1<4], na.rm=TRUE)
mean(hdata$pt.day.1[hdata$jaam.score.day.1>=4], na.rm=TRUE)
sd(hdata$pt.day.1[hdata$jaam.score.day.1>=4], na.rm=TRUE)
mean(hdata$pt.inr.day.1[hdata$jaam.score.day.1>=4], na.rm=TRUE)
sd(hdata$pt.inr.day.1[hdata$jaam.score.day.1>=4], na.rm=TRUE)
mean(hdata$pt.inr.day.1[hdata$jaam.score.day.1<4], na.rm=TRUE)
sd(hdata$pt.inr.day.1[hdata$jaam.score.day.1<4], na.rm=TRUE)
mean(hdata$fibrinogen.day.1[hdata$jaam.score.day.1>=4], na.rm=TRUE)
sd(hdata$fibrinogen.day.1[hdata$jaam.score.day.1>=4], na.rm=TRUE)
mean(hdata$fibrinogen.day.1[hdata$jaam.score.day.1<4], na.rm=TRUE)
sd(hdata$fibrinogen.day.1[hdata$jaam.score.day.1<4], na.rm=TRUE)

print(t.test(hdata$deceased.within.28.days[hdata$jaam.score.day.1<4] | hdata$hospital.expire[hdata$jaam.score.day.1<4], hdata$deceased.within.28.days[hdata$jaam.score.day.1>=4] | hdata$hospital.expire[hdata$jaam.score.day.1>=4], na.rm=TRUE))

jaam.no.na <- hdata$jaam.score.day.1[!is.na(hdata$jaam.score.day.1)]
has.jaam <- jaam.no.na[jaam.no.na>=4]
no.jaam <- jaam.no.na[jaam.no.na<4]

power.t.test(delta=mean(has.jaam)-mean(no.jaam),
	n=length(jaam.no.na),
	type="one.sample", alternative="one.sided")

attach(hdata)

has.jaam.dic.day.1 <- jaam.score.day.1>=4
no.jaam.dic.day.1 <- jaam.score.day.1<4

delta.jaam <- jaam.score.day.1 - jaam.score.day.4
## Stepwise logistic regression
fm = glm(deceased.within.28.days ~ age + jaam.score.day.1 + delta.jaam + fibrinogen.day.1, family=binomial)
summary(fm)
round(confint(fm), 3) # 95% CI for the coefficients
round(exp(coef(fm)), 3) # exponentiated coefficients
round(exp(confint(fm)), 3) # 95% CI for exponentiated coefficients
#predict(fm, type="response") # predicted values
#residuals(fm, type="deviance") # residuals

A.group <- age[!is.na(hdata$jaam.score.day.1) & hdata$jaam.score.day.1>=4]
B.group <- age[!is.na(hdata$jaam.score.day.1) & hdata$jaam.score.day.1<4]
print(t.test(A.group, B.group, var.equal=TRUE))

A.group <- MODS.day.1[!is.na(jaam.score.day.1) & !is.na(MODS.day.1) & jaam.score.day.1>=4]
B.group <- MODS.day.1[!is.na(jaam.score.day.1) & !is.na(MODS.day.1) & jaam.score.day.1<4]
print(t.test(A.group, B.group))

A.group <- SOFA.score.day.1[!is.na(jaam.score.day.1) & !is.na(SOFA.score.day.1) & jaam.score.day.1>=4]
B.group <- SOFA.score.day.1[!is.na(jaam.score.day.1) & !is.na(SOFA.score.day.1) & jaam.score.day.1<4]
print(t.test(A.group, B.group))

A.group <- sirs.day.1[!is.na(jaam.score.day.1) & !is.na(sirs.day.1) & jaam.score.day.1>=4]
B.group <- sirs.day.1[!is.na(jaam.score.day.1) & !is.na(sirs.day.1) & jaam.score.day.1<4]
print(t.test(A.group, B.group))

A.group <- fibrinogen.day.1[!is.na(jaam.score.day.1) & !is.na(fibrinogen.day.1) & jaam.score.day.1>=4]
B.group <- fibrinogen.day.1[!is.na(jaam.score.day.1) & !is.na(fibrinogen.day.1) & jaam.score.day.1<4]
print(t.test(A.group, B.group))

A.group <- pt.inr.day.1[!is.na(jaam.score.day.1) & !is.na(pt.inr.day.1) & jaam.score.day.1>=4]
B.group <- pt.inr.day.1[!is.na(jaam.score.day.1) & !is.na(pt.inr.day.1) & jaam.score.day.1<4]
print(t.test(A.group, B.group))

A.group <- pt.day.1[!is.na(jaam.score.day.1) & !is.na(pt.day.1) & jaam.score.day.1>=4]
B.group <- pt.day.1[!is.na(jaam.score.day.1) & !is.na(pt.day.1) & jaam.score.day.1<4]
print(t.test(A.group, B.group))

A.group <- platelets.day.1[!is.na(jaam.score.day.1) & !is.na(platelets.day.1) & jaam.score.day.1>=4]
B.group <- platelets.day.1[!is.na(jaam.score.day.1) & !is.na(platelets.day.1) & jaam.score.day.1<4]
print(t.test(A.group, B.group))

A.group <- jaam.score.day.1[!is.na(jaam.score.day.1) & jaam.score.day.1>=4]
B.group <- jaam.score.day.1[!is.na(jaam.score.day.1) & jaam.score.day.1<4]
print(t.test(A.group, B.group))

jaam.score.no.na <- !is.na(jaam.score.day.1)
t.test(gender[jaam.score.no.na & jaam.score.day.1<4], gender[jaam.score.no.na & jaam.score.day.1>=4])
t.test(jaam.score.day.1[jaam.score.no.na & jaam.score.day.1<4], jaam.score.day.1[jaam.score.no.na & jaam.score.day.1>=4])
sd(jaam.score.day.1[jaam.score.no.na & jaam.score.day.1<4])

t.test(platelets.day.1[jaam.score.no.na & jaam.score.day.1<4], platelets.day.1[jaam.score.no.na & jaam.score.day.1>=4])
sd(platelets.day.1[jaam.score.no.na & jaam.score.day.1<4])
sd(platelets.day.1[jaam.score.no.na & jaam.score.day.1<4 & !is.na(platelets.day.1)])
sd(platelets.day.1[jaam.score.no.na & jaam.score.day.1>4 & !is.na(platelets.day.1)])
sd(platelets.day.1[jaam.score.no.na & jaam.score.day.1>=4 & !is.na(platelets.day.1)])

var.test(fibrinogen.day.1[!is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1) & jaam.score.day.1>=4], fibrinogen.day.1[!is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1) & jaam.score.day.1<4])
t.test(fibrinogen.day.1[!is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1) & jaam.score.day.1>=4], fibrinogen.day.1[!is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1) & jaam.score.day.1<4])

sd(fibrinogen.day.1[!is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1) & jaam.score.day.1>=4])
sd(fibrinogen.day.1[!is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1) & jaam.score.day.1<4])


print(power.t.test(delta=mean(hospital.expire[jaam.score.day.1>=4 & !is.na(jaam.score.day.1)])-mean(hospital.expire[jaam.score.day.1<4 & !is.na(jaam.score.day.1)]), n=min(sum(!is.na(jaam.score.day.1) & jaam.score.day.1>=4), sum(!is.na(jaam.score.day.1) & jaam.score.day.1<4)), sd=sd(hospital.expire[!is.na(jaam.score.day.1)])))

power.t.test(delta=mean(delta.jaam[jaam.score.day.1>=4 & !is.na(delta.jaam)])-mean(delta.jaam[jaam.score.day.1<4 & !is.na(delta.jaam)]), n=min(sum(!is.na(delta.jaam) & jaam.score.day.1>=4), sum(!is.na(delta.jaam) & jaam.score.day.1<4)), sd=sd(delta.jaam[!is.na(delta.jaam)]))

power.t.test(delta=mean(fibrinogen.day.1[jaam.score.day.1>=4 & !is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1)])-
	mean(fibrinogen.day.1[jaam.score.day.1<4 & !is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1)]),
	n=min(sum(!is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1) & jaam.score.day.1>=4),
	sum(!is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1) & jaam.score.day.1<4)),
	sd=sd(fibrinogen.day.1[!is.na(fibrinogen.day.1) & !is.na(jaam.score.day.1)]))



## Table 6
message("Calculating table 5")
message(paste0("Total males = ", sum(gender, na.rm=TRUE)))
message(paste0("Total females = ", sum(!gender, na.rm=TRUE)))
message(paste0("28-day mortality in patients with JAAM DIC = ", round(100*sum(jaam.score.day.1>=4 & deceased.within.28.days, na.rm=TRUE)/sum(jaam.score.day.1>=4, na.rm=TRUE), 2)))
message(paste0("28-day mortality in patients without JAAM DIC = ", round(100*sum(jaam.score.day.1<4 & deceased.within.28.days, na.rm=TRUE)/sum(jaam.score.day.1<4, na.rm=TRUE), 2)))

message("Calculating table 6")
message(paste0("JAAM DIC n = ", sum(jaam.score.day.1>=4, na.rm=TRUE)))
message(paste0("Non-DIC n = ", sum(jaam.score.day.1<4, na.rm=TRUE)))
missing.data(jaam.score.day.1)
sum(gender[jaam.score.day.1>=4], na.rm=TRUE)
sum(!gender[jaam.score.day.1>=4], na.rm=TRUE)
sum(gender[jaam.score.day.1<4], na.rm=TRUE)
sum(!gender[jaam.score.day.1<4], na.rm=TRUE)
table6.row(platelets.day.1)
table6.row(pt.day.1)
table6.row(pt.inr.day.1)
table6.row(fibrinogen.day.1)
table6.row(fdp.day.1)
table6.row(sirs.day.1)
table6.row(saps1.day.1)
table6.row(SOFA.score.day.1)
table6.row(MODS.day.1)
table6.row(MODS.day.1, 3)
table6.row(deceased.within.28.days, 3)
t.test(gender[has.jaam.dic.day.1], gender[no.jaam.dic.day.1], na.rm=TRUE)

message("Calculating table 6")